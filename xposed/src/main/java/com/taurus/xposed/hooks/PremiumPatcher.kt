package com.taurus.xposed.hooks

import com.taurus.xposed.proto.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Patches the binary protobuf bodies that Spotify Android receives from
 * spclient.wg.spotify.com on two endpoints:
 *
 *   POST /user-customization-service/v1/customize   (bootstrap attribute fetch)
 *   GET  /user-customization-service/v1/customize   (per-session attribute poll)
 *
 * The patching logic is a direct port of Premium.m from the iOS tweak.
 * Field numbers come from live captures — they match what the iOS code observed.
 */
object PremiumPatcher {

    // ── account attributes ────────────────────────────────────────────────────

    private fun forcedAttributes(): Map<String, Any> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.YEAR, 1)
        val yearOn = sdf.format(cal.time)

        return mapOf(
            "ads"                               to false,
            "ab-ad-player-targeting"            to "0",
            "allow-advertising-id-transmission" to false,
            "restrict-advertising-id-transmission" to true,
            "can_use_superbird"                 to true,
            "enable-crossfade-product-state"    to "1",
            "enable-gapless-product-state"      to "1",
            "catalogue"                         to "premium",
            "financial-product"                 to "pr:premium,tc:0",
            "is-eligible-premium-unboxing"      to true,
            "name"                              to "Spotify Premium",
            "nft-disabled"                      to "1",
            "on-demand"                         to true,
            "player-license"                    to "premium",
            "player-license-v2"                 to "premium",
            "product-expiry"                    to yearOn,
            "shuffle-eligible"                  to true,
            "streaming-rules"                   to "",
            "subscription-enddate"              to yearOn,
            "type"                              to "premium",
            "unrestricted"                      to true,
            "high-bitrate"                      to true,
            "loudness-levels"                   to "1:-5.0,0.0,3.0:-2.0",
            "pick-and-shuffle"                  to false,
            "mixing-tools"                      to "EDIT",
            "jam-social-session"                to "EXPANDED",
            "jam-queue-commands-feedback"        to "1",
            "social-session"                    to true,
            "social-session-free-tier"          to false,
            "your-library-tags"                 to true,
            "libspotify"                        to true,
            "mobile"                            to true,
        )
    }

    private val strippedKeys = setOf(
        "payment-state", "last-premium-activation-date", "on-demand-trial",
        "on-demand-trial-in-progress", "smart-shuffle", "at-signal",
        "feature-set-id-masked", "strider-key", "is-eligible-for-trial",
        "is-eligible-for-upsell", "upsell-state", "ad-session-persistence",
        "ad-formats-preroll-video",
    )

    private fun stripped(key: String): Boolean =
        key.startsWith("is-premium-eligible") || key in strippedKeys

    private fun attributeEntry(key: String, value: Any): PBField {
        val valueField = if (value is String) pbString(4, value) else pbVarint(2, value as Boolean)
        return pbBytes(1, pbSerialize(listOf(pbString(1, key), pbBytes(2, pbSerialize(listOf(valueField))))))
    }

    private fun patchAttributes(data: ByteArray): ByteArray? {
        val fields = pbParse(data) ?: return null
        val forced = forcedAttributes()
        val out = mutableListOf<PBField>()
        val seen = mutableSetOf<String>()

        for (field in fields) {
            val key = if (field.number == 1 && field.wire == 2)
                pbText(pbFirst(pbParse(field.payload), 1))
            else null

            if (key == null || (!forced.containsKey(key) && !stripped(key))) {
                out += field; continue
            }
            if (!forced.containsKey(key)) continue
            seen += key
            out += attributeEntry(key, forced[key]!!)
        }
        for ((key, value) in forced) {
            if (key !in seen) out += attributeEntry(key, value)
        }
        return pbSerialize(out)
    }

    // ── remote config rules ────────────────────────────────────────────────────

    private enum class RuleKind { REMOVE, SET, FORCE, ENUM }
    private data class Rule(val scope: String?, val name: String?, val kind: RuleKind, val on: Boolean = false, val text: String? = null)

    private val rules: List<Rule> = buildList {
        fun rem(scope: String? = null, name: String? = null) = add(Rule(scope, name, RuleKind.REMOVE))
        fun set(name: String, on: Boolean) = add(Rule(null, name, RuleKind.SET, on))
        fun force(scope: String, name: String, on: Boolean) = add(Rule(scope, name, RuleKind.FORCE, on))
        fun enum(name: String, text: String) = add(Rule(null, name, RuleKind.ENUM, text = text))

        force("android-feature-settings", "crossfade_enabled", true)
        force("android-feature-settings", "automix_enabled", true)
        force("core-playback-setup", "crossfade_enabled", true)
        force("core-automix", "automix_enabled", true)
        force("core-ads", "enable_state_fetch_system", false)
        force("core-ad-detection", "use_ad_placements_beta", false)

        for (n in listOf(
            "enable_common_capping", "enable_pns_common_capping",
            "enable_pick_and_shuffle_common_capping", "enable_pick_and_shuffle_dynamic_cap",
            "pick_and_shuffle_timecap", "enable_free_on_demand_experiment",
            "enable_free_on_demand_context_menu_experiment", "enable_mft_plus_queue",
            "enable_mft_plus_extended_queue", "ad_metadata", "ad_slots", "ad_on_app_open_enabled",
            "adonappopen_enabled", "is_ad_on_app_open_enabled",
        )) rem(name = n)

        rem(scope = "android-feature-queue")
        rem(scope = "android-ad-on-app-open")
        rem(scope = "android-feature-adonappopen")
        rem(scope = "marquee")
        rem(scope = "android-feature-marquee")
        rem(scope = "leavebehindadsbase")
        rem(scope = "android-feature-leavebehindadsbase")
        rem(scope = "android-feature-instreamads")
        rem(scope = "android-adsembedded-embeddedctaelements-impl")
        rem(scope = "android-adsnowplaying-embeddednpv-impl")
        rem(scope = "android-adsplatform-elementimpl")
        rem(scope = "android-system-adssponsoredcontext")
        rem(scope = "android-feature-adsswift")

        set("enable_playback_timeout_service", false)
        set("enable_playback_timeout_error_ui", false)
        enum("playback_timeout_action", "Nothing")

        for (n in listOf(
            "ads", "enable_ads", "enable_audio_ads", "enable_display_ads", "enable_video_ads",
            "enable_premium_upsell", "enable_upsell", "show_upsell", "show_premium_upsell",
            "enable_campaigns", "enable_promotions", "enable_popups",
            "enable_leave_behind_ads_card_element", "music_npv_leavebehinds_enabled",
            "enable_ads_on_podcast", "enable_display_element", "enable_video_element",
            "is_promo_cta_enabled", "show_time_cap_upsell_with_premium_badge",
            "enable_video_time_cap_upsell", "enable_video_time_cap_upsell_on_search",
            "music_video_upsell_enabled", "is_gbb_upsell_enabled",
            "should_show_pigeon_upsell", "is_enabled_pt2",
            "enable_now_playing_ad", "enable_now_playing_ads",
            "enable_now_playing_banner_ad", "enable_now_playing_banner_ads",
            "enable_search_ad", "enable_search_ads",
            "enable_search_banner_ad", "enable_search_banner_ads",
            "enable_home_ad", "enable_home_ads",
            "enable_home_banner_ad", "enable_home_banner_ads",
            "enable_artist_ad", "enable_artist_ads",
            "enable_artist_banner_ad", "enable_artist_banner_ads",
            "enable_playlist_ad", "enable_playlist_ads",
            "enable_album_ad", "enable_album_ads",
            "enable_library_ad", "enable_library_ads",
            "enable_audiobook_ad", "enable_audiobook_ads",
            "enable_podcast_ad", "enable_podcast_ads",
            "enable_sponsored_content", "enable_sponsored_playlists",
            "enable_sponsored_sessions", "enable_sponsored_stories",
            "enable_sponsored_videos", "enable_sponsored_artist",
            "enable_sponsored_artists", "enable_sponsored_album",
            "enable_sponsored_albums", "enable_sponsored_track",
            "enable_sponsored_tracks", "enable_sponsored_show",
            "enable_sponsored_shows", "enable_sponsored_episode",
            "enable_sponsored_episodes", "enable_sponsored_audiobook",
            "enable_sponsored_audiobooks", "enable_sponsored_podcast",
            "enable_sponsored_podcasts", "enable_sponsored_search",
            "enable_sponsored_search_results", "enable_sponsored_home",
            "enable_popup", "show_popups", "show_popup",
            "enable_interstitials", "enable_interstitial",
            "enable_overlays", "enable_overlay",
            "enable_promotions_on_home", "enable_promotions_on_search",
            "enable_search_page_ads", "enable_home_page_ads",
            "enable_billboard", "enable_billboards",
            "enable_audio_ads_player", "enable_display_ads_player",
            "enable_video_ads_player",
        )) set(n, false)

        set("disable_suggested_tracks_upsell", true)
        set("show_skip_button_during_skippable_ads", true)
        set("sponsored_playlist_v2_header_dismissible", true)
        set("use_mock_sponsorship_endpoint", false)

        force("android-reinventfree-contextualupsellpremiumpromo-impl", "is_promo_cta_enabled", false)
        force("android-reinventfree-contextualupsellpremiumpromo-impl", "show_time_cap_upsell_with_premium_badge", false)
        force("android-reinventfree-controllerui-impl", "enable_video_time_cap_upsell", false)
        force("android-reinventfree-controllerui-impl", "enable_video_time_cap_upsell_on_search", false)
        force("android-settings-mediaqualitypageplugin-impl", "is_gbb_upsell_enabled", false)
        force("android-settings-mediaqualitypageplugin-impl", "should_show_pigeon_upsell", false)
        force("android-system-listeningparties", "preview_ended_upsell_enabled", false)
        force("android-feature-shuffletoggleupsell", "is_enabled_pt2", false)
        force("android-feature-shuffletoggleupsell", "linear_upsell_new_style_experiment_enabled", false)
        force("android-feature-shuffletoggleupsell", "play_modes_upsell_new_style_experiment_enabled", false)
        force("android-jam-freeusershuffleupsellsheetpage-impl", "free_user_shuffle_upsell_sheet_enabled", false)
        force("android-jam-freeuserskipupsellpage-impl", "free_user_skip_upsell_sheet_enabled", false)
        force("android-jam-freehostedjamsupsell-impl", "free_hosted_jams_upsell_enabled", false)
        force("android-jam-adsdisclaimersheetpage-impl", "ads_disclaimer_sheet_enabled", false)
        force("android-sociallistening-configuration-impl", "free_user_enabled_to_host_in_person_jam", true)
        force("android-sociallistening-configuration-impl", "is_nearby_jam_invite_deeplink_enabled", true)
        force("android-sociallistening-configuration-impl", "join_sheet_from_join_options_enabled", true)
        force("android-sociallistening-configuration-impl", "nearby_session_invitation_enabled", true)
    }

    private fun ruleMatches(rule: Rule, scope: String, name: String): Boolean {
        if (rule.scope != null && rule.scope != scope) return false
        return rule.name == null || rule.name == name
    }

    private fun boolValue(on: Boolean): ByteArray =
        if (on) pbSerialize(listOf(pbVarint(1, true))) else ByteArray(0)

    private fun setValue(value: MutableList<PBField>, replacement: PBField) {
        value.removeAll { it.number in 3..5 }
        value += replacement
    }

    private fun patchConfiguration(data: ByteArray): ByteArray? {
        val fields = pbParse(data) ?: return null
        val out = mutableListOf<PBField>()
        val present = mutableSetOf<String>()

        for (field in fields) {
            val value: MutableList<PBField>? =
                if (field.number == 3 && field.wire == 2) pbParse(field.payload)?.toMutableList() else null
            val identifier = pbFirst(value, 1)
            val parts = if (identifier != null) pbParse(identifier.payload) else null

            if (parts == null) { out += field; continue }

            val scope = pbText(pbFirst(parts, 1)) ?: ""
            val name = pbText(pbFirst(parts, 2)) ?: ""
            var removed = false

            for (rule in rules) {
                if (!ruleMatches(rule, scope, name)) continue
                when (rule.kind) {
                    RuleKind.REMOVE -> { removed = true; break }
                    RuleKind.SET -> {
                        if (pbFirst(value, 3) != null)
                            setValue(value!!, pbBytes(3, boolValue(rule.on)))
                    }
                    RuleKind.FORCE -> setValue(value!!, pbBytes(3, boolValue(rule.on)))
                    RuleKind.ENUM -> setValue(value!!, pbBytes(5, pbSerialize(listOf(pbString(1, rule.text!!)))))
                }
            }
            if (removed) continue
            present += "$scope.$name"
            if (value != null) field.payload = pbSerialize(value)
            out += field
        }

        // inject FORCE rules that weren't present
        for (rule in rules) {
            if (rule.kind != RuleKind.FORCE || rule.scope == null || rule.name == null) continue
            if ("${rule.scope}.${rule.name}" in present) continue
            val id = pbSerialize(listOf(pbString(1, rule.scope), pbString(2, rule.name)))
            out += pbBytes(3, pbSerialize(listOf(pbBytes(1, id), pbBytes(3, boolValue(rule.on)))))
        }

        return pbSerialize(out)
    }

    private fun patchUcs(data: ByteArray): ByteArray? {
        val fields = pbParse(data)?.toMutableList() ?: return null

        // field 1 = configuration tree
        val resolve = pbFirst(fields, 1)
        if (resolve?.wire == 2) {
            val edited = pbEdit(resolve.payload, listOf(1)) { patchConfiguration(it) }
            if (edited != null) resolve.payload = edited
        }

        // field 3 = account attributes
        var attributes = pbFirst(fields, 3)
        if (attributes == null) {
            attributes = pbBytes(3, ByteArray(0))
            fields += attributes
        }
        if (attributes.wire == 2) {
            attributes.payload = patchAttributes(attributes.payload) ?: attributes.payload
        }

        return pbSerialize(fields)
    }

    // ── public entry points ───────────────────────────────────────────────────

    /** Patch the /customize poll response (field path [1] → ucs). */
    fun patchCustomize(body: ByteArray): ByteArray =
        pbEdit(body, listOf(1)) { patchUcs(it) } ?: body

    /** Patch the /bootstrap response (field path [2,1,1,1] → ucs). */
    fun patchBootstrap(body: ByteArray): ByteArray =
        pbEdit(body, listOf(2, 1, 1, 1)) { patchUcs(it) } ?: body
}
