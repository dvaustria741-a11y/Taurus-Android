package com.taurus.xposed

import com.taurus.xposed.hooks.SpotifyHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class TaurusXposed : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpp: LoadPackageParam) {
        XposedBridge.log("Taurus: loading into ${lpp.packageName}")
        SpotifyHook.install(lpp)
    }
}
