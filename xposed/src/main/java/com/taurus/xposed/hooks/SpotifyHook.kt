package com.taurus.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Hooks Spotify Android's HTTP layer to intercept UCS (User Customization Service)
 * responses and patch them with premium attributes + ad-block config.
 *
 * Strategy: Spotify ships OkHttp inside its APK. We hook
 *   okhttp3.OkHttpClient$Builder.build() — called once per client instance — and append
 *   our [TaurusInterceptor] to every client that gets built. Our interceptor then rewrites
 *   the response body for the two UCS endpoints.
 *
 * Fallback: if the class name has been obfuscated (Spotify aggressively ProGuards), we
 * try a secondary hook on the Interceptor interface's intercept() method by scanning all
 * loaded classes for the OkHttp signature.
 */
object SpotifyHook {

    private const val SPOTIFY = "com.spotify.music"
    private val UCS_CUSTOMIZE = Regex("/user-customization-service/v1/customize")
    private val UCS_BOOTSTRAP  = Regex("/bootstrap")

    fun install(lpp: LoadPackageParam) {
        if (lpp.packageName != SPOTIFY) return
        try {
            hookOkHttpBuilder(lpp.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log("Taurus: primary hook failed — $t")
            try {
                hookCallExecute(lpp.classLoader)
            } catch (t2: Throwable) {
                XposedBridge.log("Taurus: fallback hook failed — $t2")
            }
        }
    }

    // ── primary hook: okhttp3.OkHttpClient$Builder.build() ────────────────────

    private fun hookOkHttpBuilder(cl: ClassLoader) {
        val builder = XposedHelpers.findClass("okhttp3.OkHttpClient\$Builder", cl)
        XposedBridge.hookAllMethods(builder, "build", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val interceptors = XposedHelpers.getObjectField(param.thisObject, "interceptors")
                            as? MutableList<Any> ?: return
                    interceptors.add(0, buildOkHttpInterceptor(cl))
                } catch (_: Throwable) {}
            }
        })
        XposedBridge.log("Taurus: OkHttpClient.Builder hook installed")
    }

    /**
     * Builds a java.lang.reflect.Proxy that implements okhttp3.Interceptor.
     * We can't reference OkHttp classes at compile time since Spotify's copy
     * may be renamed; instead we wire everything up reflectively at runtime.
     */
    private fun buildOkHttpInterceptor(cl: ClassLoader): Any {
        val interceptorClass = XposedHelpers.findClass("okhttp3.Interceptor", cl)
        return java.lang.reflect.Proxy.newProxyInstance(cl, arrayOf(interceptorClass)) { _, method, args ->
            if (method.name != "intercept" || args == null || args.isEmpty()) {
                return@newProxyInstance null
            }
            val chain = args[0] ?: return@newProxyInstance null

            // chain.request()
            val request = chain.javaClass.getMethod("request").invoke(chain)
            val urlObj  = request.javaClass.getMethod("url").invoke(request)
            val urlStr  = urlObj.javaClass.getMethod("toString").invoke(urlObj) as? String ?: ""

            // chain.proceed(request)
            val response = chain.javaClass.getMethod("proceed", request.javaClass).invoke(chain, request)
                ?: return@newProxyInstance null

            // only patch UCS endpoints
            if (!urlStr.contains("user-customization-service") && !urlStr.contains("bootstrap")) {
                return@newProxyInstance response
            }

            try {
                patchResponse(response, urlStr, cl)
            } catch (t: Throwable) {
                XposedBridge.log("Taurus: patch error for $urlStr — $t")
                response
            }
        }
    }

    private fun patchResponse(response: Any, url: String, cl: ClassLoader): Any {
        val bodyObj = response.javaClass.getMethod("body").invoke(response) ?: return response
        val bytes   = bodyObj.javaClass.getMethod("bytes").invoke(bodyObj) as? ByteArray ?: return response

        val patched = when {
            UCS_CUSTOMIZE.containsMatchIn(url) -> PremiumPatcher.patchCustomize(bytes)
            UCS_BOOTSTRAP.containsMatchIn(url) -> PremiumPatcher.patchBootstrap(bytes)
            else -> return response
        }
        if (patched.contentEquals(bytes)) return response

        // Rebuild: ResponseBody.create(contentType, patched)
        val responseBodyClass  = XposedHelpers.findClass("okhttp3.ResponseBody", cl)
        val mediaTypeClass      = XposedHelpers.findClass("okhttp3.MediaType", cl)
        val contentTypeMethod  = bodyObj.javaClass.getMethod("contentType")
        val contentType        = contentTypeMethod.invoke(bodyObj)

        val newBody = responseBodyClass.getMethod("create", mediaTypeClass, ByteArray::class.java)
            .invoke(null, contentType, patched)

        // response.newBuilder().body(newBody).build()
        val builder = response.javaClass.getMethod("newBuilder").invoke(response)
        builder.javaClass.getMethod("body", responseBodyClass).invoke(builder, newBody)
        return builder.javaClass.getMethod("build").invoke(builder)
    }

    // ── fallback hook: RealCall.execute() ─────────────────────────────────────

    private fun hookCallExecute(cl: ClassLoader) {
        val realCall = XposedHelpers.findClass("okhttp3.internal.connection.RealCall", cl)
        XposedBridge.hookAllMethods(realCall, "execute", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val response = param.result ?: return
                try {
                    val request = XposedHelpers.callMethod(response, "request") ?: return
                    val url     = XposedHelpers.callMethod(
                        XposedHelpers.callMethod(request, "url"), "toString"
                    ) as? String ?: return
                    param.result = patchResponse(response, url, cl)
                } catch (_: Throwable) {}
            }
        })
        XposedBridge.log("Taurus: RealCall.execute fallback hook installed")
    }
}
