package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.model.data.AppInfo

class CategoryProvider(private val context: Context) {

    fun getCategory(appInfo: AppInfo): AppLibraryCategory {
        val packageName = (appInfo.componentName?.packageName ?: "").lowercase()
        val appName = appInfo.title.toString().lowercase().trim()

        // 1. Known Package DB (Highest priority for accuracy)
        getCategoryByPackage(packageName)?.let { return it }

        // 2. Developer Rules (e.g. Adobe, Microsoft)
        getCategoryByDeveloper(packageName)?.let { return it }

        // 3. Keyword Rules based on App Name
        getCategoryByKeyword(appName)?.let { return it }

        // 4. ApplicationInfo.category (API 26+)
        val pm = context.packageManager
        try {
            val appInfoFlags = pm.getApplicationInfo(packageName, 0)
            getCategoryByAppInfoCategory(appInfoFlags.category, appName, packageName)?.let { return it }
        } catch (e: Exception) {
            // Ignore if package not found
        }

        // 5. Fallback
        return AppLibraryCategory.OTHER
    }

    private fun getCategoryByDeveloper(packageName: String): AppLibraryCategory? {
        if (packageName.startsWith("com.adobe.")) return AppLibraryCategory.CREATIVITY
        if (packageName.startsWith("com.microsoft.")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.jetbrains.")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.google.android.apps.docs")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.google.android.apps.fitness")) return AppLibraryCategory.HEALTH_FITNESS
        return null
    }

    private fun getCategoryByPackage(packageName: String): AppLibraryCategory? {
        return when (packageName) {
            // Social: Dialers, Messaging, Contacts, Social Media & Chat
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.coloros.phonemanager", "com.oplus.dialer", "com.coloros.dialer",
            "com.google.android.contacts", "com.android.contacts", "com.samsung.android.app.contacts",
            "com.coloros.contacts", "com.oplus.contacts",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            "com.coloros.mms", "com.oplus.mms",
            "com.whatsapp", "com.whatsapp.w4b", "com.facebook.katana", "com.facebook.orca",
            "com.facebook.lite", "com.instagram.android", "com.twitter.android",
            "com.snapchat.android", "org.telegram.messenger", "org.telegram.plus",
            "org.thoughtcrime.securesms", "com.discord", "com.viber.voip", "com.skype.raider",
            "com.tencent.mm", "jp.naver.line.android", "com.kakao.talk",
            "com.google.android.apps.tachyon", "us.zoom.videomeetings",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> AppLibraryCategory.SOCIAL

            // Utilities: System essentials, App Store, Settings, Clock, Calculator, Weather, Browsers
            "com.android.vending", "com.android.settings", "com.google.android.settings",
            "com.coloros.settings", "com.oplus.settings", "com.samsung.android.settings",
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock", "com.oplus.alarmclock",
            "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator",
            "com.coloros.calculator", "com.oplus.calculator",
            "com.google.android.apps.weather", "com.sec.android.daemonapp", "com.coloros.weather2",
            "com.oplus.weather",
            "com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx",
            "com.opera.browser", "com.brave.browser", "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser", "com.android.stk" -> AppLibraryCategory.UTILITIES

            // Tools: File managers, hardware tools, voice recorders, terminals, diagnostics
            "com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles", "com.coloros.filemanager", "com.oplus.filemanager",
            "com.google.android.soundpicker", "com.android.soundrecorder", "com.coloros.soundrecorder",
            "com.oplus.soundrecorder", "com.sec.android.app.voicenote", "com.termux",
            "org.speedtest.android", "org.zwanoo.android.speedtest" -> AppLibraryCategory.TOOLS

            // Finance: Wallets, UPI, Payments, Banks, Crypto, Investing
            "com.google.android.apps.walletnf", "com.google.android.apps.nbu.paisa.user",
            "com.paypal.android.p2pmobile", "com.squareup.cash", "com.venmo",
            "net.one97.paytm", "com.phonepe.app", "in.org.npci.upiapp",
            "com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile",
            "com.americanexpress.android.acctsvcs.us", "com.capitalone.mobile", "com.citi.citimobile",
            "com.revolut.revolut", "co.uk.monzo", "com.discoverfinancial.mobile",
            "com.usbank.mobilebanking", "com.sbi.lotusintouch", "com.msf.kbank.mobile",
            "com.csam.icici.bank.imobile", "com.hdfcbank.corebanking", "com.axis.mobile",
            "com.robinhood.android", "com.coinbase.android", "com.binance.dev",
            "com.creditkarma.mobile", "com.zerodha.kite3", "com.nextbillion.groww",
            "com.webull.mn", "com.etrade.mobilepro" -> AppLibraryCategory.FINANCE

            // Productivity: Email, Calendars, Notes, Office, Cloud docs & storage
            "com.google.android.gm", "com.android.email", "com.microsoft.office.outlook",
            "com.samsung.android.email.provider", "com.yahoo.mobile.client.android.mail",
            "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar",
            "com.coloros.calendar", "com.oplus.calendar",
            "com.google.android.keep", "com.coloros.note", "com.oplus.note",
            "com.samsung.android.app.notes", "com.evernote",
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.microsoft.office.officehubrow", "com.microsoft.office.onenote",
            "notion.id", "com.dropbox.android", "com.microsoft.skydrive",
            "com.todoist", "com.ticktick.task", "com.trello", "com.asana.app", "com.slack" -> AppLibraryCategory.PRODUCTIVITY

            // Creativity: Photos, Camera, Design, Art, Photo/Video editing
            "com.google.android.apps.photos", "com.google.android.camera", "com.android.camera2",
            "com.android.camera", "com.sec.android.app.camera", "com.oplus.camera",
            "com.coloros.camera", "com.google.android.gallery3d", "com.android.gallery3d",
            "com.sec.android.gallery3d", "com.coloros.gallery3d", "com.oplus.gallery",
            "com.adobe.psmobile", "com.adobe.lrmobile", "com.niksoftware.snapseed",
            "com.canva.editor", "com.picsart.studio", "com.camerasideas.instashot",
            "com.capcut.android" -> AppLibraryCategory.CREATIVITY

            // Entertainment: Music, Video, Streaming
            "com.spotify.music", "com.netflix.mediaclient", "com.google.android.youtube",
            "com.google.android.apps.youtube.music", "tv.twitch.android.app", "com.amazon.mp3",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.hbo.hbonow",
            "com.apple.android.music", "com.soundcloud.android", "com.shazam.android" -> AppLibraryCategory.ENTERTAINMENT

            // Games
            "com.nianticlabs.pokemongo", "com.supercell.clashofclans", "com.king.candycrushsaga",
            "com.supercell.clashroyale", "com.roblox.client", "com.epicgames.portal",
            "com.ea.gp.fifamobile", "com.krafton.pubg", "com.tencent.ig" -> AppLibraryCategory.GAMES

            // Travel: Maps, Rides, Nav
            "com.google.android.apps.maps", "com.waze", "com.moovit",
            "com.ubercab", "com.lyft.android", "com.booking", "com.airbnb.android",
            "com.tripadvisor.tripadvisor", "com.olacabs.customer" -> AppLibraryCategory.TRAVEL

            // Shopping & Food
            "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.ubercab.eats",
            "com.walmart.android", "com.target.ui", "com.flipkart.android",
            "com.myntra.android", "com.zomato", "in.swiggy.android",
            "com.doordash.driverapp", "com.dd.doordash", "com.grubhub.android",
            "com.instacart.client" -> AppLibraryCategory.SHOPPING_FOOD

            // Information & Reading
            "com.reddit.frontpage", "com.google.android.apps.magazines",
            "com.google.android.apps.books", "com.amazon.kindle", "com.medium.reader" -> AppLibraryCategory.INFORMATION_READING

            // Health & Fitness
            "com.google.android.apps.fitness", "com.strava", "com.myfitnesspal.android",
            "com.fitbit.FitbitMobile", "com.nike.plusgps" -> AppLibraryCategory.HEALTH_FITNESS

            // Education
            "com.duolingo", "org.khanacademy.android", "com.coursera.android" -> AppLibraryCategory.EDUCATION

            else -> null
        }
    }

    private fun getCategoryByKeyword(appName: String): AppLibraryCategory? {
        // App Stores & Essential Utilities first (prevent "store" triggering shopping)
        if (appName.contains("play store") || appName.contains("app store") ||
            appName.contains("setting") || appName.contains("clock") ||
            appName.contains("alarm") || appName.contains("timer") ||
            appName.contains("stopwatch") || appName.contains("calc") ||
            appName.contains("weather") || appName.contains("forecast") ||
            appName.contains("browser") || appName.contains("util")) {
            return AppLibraryCategory.UTILITIES
        }

        // Tools: File managers, Cleaners, Scanners, Compass, Flashlight, Recorders, Terminals
        if (appName.contains("file") || appName.contains("tool") ||
            appName.contains("cleaner") || appName.contains("scanner") ||
            appName.contains("compass") || appName.contains("flashlight") ||
            appName.contains("recorder") || appName.contains("terminal") ||
            appName.contains("speedtest") || appName.contains("meter") ||
            appName.contains("benchmark") || appName.contains("backup")) {
            return AppLibraryCategory.TOOLS
        }

        // Finance: Banking, Wallets, Payments, Crypto, Investments
        val isFinance = appName.contains("bank") || appName.contains("finance") ||
                appName.contains("financial") || appName.contains("wallet") ||
                appName.contains("money") || appName.contains("crypto") ||
                appName.contains("bitcoin") || appName.contains("invest") ||
                appName.contains("stock") || appName.contains("trading") ||
                appName.contains("credit") || appName.contains("loan") ||
                appName.contains("budget") || appName.contains("tax") ||
                appName.contains("upi") || appName.contains("fund") ||
                (appName.contains("pay") && !appName.contains("player"))
        if (isFinance) return AppLibraryCategory.FINANCE

        // Productivity: Mail, Calendar, Notes, Docs, Cloud, Office, Tasks
        if (appName.contains("mail") || appName.contains("email") || appName.contains("gmail") ||
            appName.contains("calendar") || appName.contains("agenda") ||
            appName.contains("note") || appName.contains("task") || appName.contains("todo") ||
            appName.contains("doc") || appName.contains("sheet") || appName.contains("slide") ||
            appName.contains("office") || appName.contains("word") || appName.contains("excel") ||
            appName.contains("powerpoint") || appName.contains("drive") || appName.contains("cloud") ||
            appName.contains("pdf") || appName.contains("scanner") || appName.contains("remind") ||
            appName.contains("planner") || appName.contains("workspace") || appName.contains("notion") ||
            appName.contains("slack") || appName.contains("trello")) {
            return AppLibraryCategory.PRODUCTIVITY
        }

        // Social: Chat, Calls, Contacts, Phone, Social Media
        if (appName.contains("phone") || appName.contains("dialer") ||
            appName.contains("contact") || appName.contains("message") ||
            appName.contains("sms") || appName.contains("chat") ||
            appName.contains("messenger") || appName.contains("social") ||
            appName.contains("meet") || appName.contains("call") || appName.contains("talk")) {
            return AppLibraryCategory.SOCIAL
        }

        // Games
        if (appName.contains("game") || appName.contains("puzzle") ||
            appName.contains("rpg") || appName.contains("arcade") ||
            appName.contains("casino") || appName.contains("racing") ||
            appName.contains("quest")) {
            return AppLibraryCategory.GAMES
        }

        // Creativity: Photo, Camera, Video edit, Art
        if (appName.contains("photo") || appName.contains("camera") ||
            appName.contains("gallery") || appName.contains("draw") ||
            appName.contains("paint") || appName.contains("edit") ||
            appName.contains("design") || appName.contains("art") ||
            appName.contains("canvas") || appName.contains("studio")) {
            return AppLibraryCategory.CREATIVITY
        }

        // Entertainment: Music, Video, TV, Movies, Audio
        if (appName.contains("music") || appName.contains("audio") ||
            appName.contains("video") || appName.contains("tv") ||
            appName.contains("movie") || appName.contains("player") ||
            appName.contains("podcast") || appName.contains("stream") ||
            appName.contains("radio") || appName.contains("song")) {
            return AppLibraryCategory.ENTERTAINMENT
        }

        // Travel: Maps, Rides, Nav, Flights
        if (appName.contains("travel") || appName.contains("map") ||
            appName.contains("nav") || appName.contains("transit") ||
            appName.contains("ride") || appName.contains("taxi") ||
            appName.contains("cab") || appName.contains("flight") ||
            appName.contains("hotel") || appName.contains("trip")) {
            return AppLibraryCategory.TRAVEL
        }

        // Shopping & Food (stores, markets, delivery)
        if (appName.contains("shop") || appName.contains("store") ||
            appName.contains("food") || appName.contains("eat") ||
            appName.contains("restaurant") || appName.contains("delivery") ||
            appName.contains("grocery") || appName.contains("order")) {
            return AppLibraryCategory.SHOPPING_FOOD
        }

        // Health & Fitness
        if (appName.contains("health") || appName.contains("fitness") ||
            appName.contains("workout") || appName.contains("gym") ||
            appName.contains("diet") || appName.contains("run") ||
            appName.contains("step") || appName.contains("meditation") ||
            appName.contains("yoga")) {
            return AppLibraryCategory.HEALTH_FITNESS
        }

        // Information & Reading
        if (appName.contains("news") || appName.contains("reader") ||
            appName.contains("book") || appName.contains("ebook") ||
            appName.contains("magazine") || appName.contains("comic") ||
            appName.contains("article")) {
            return AppLibraryCategory.INFORMATION_READING
        }

        // Education
        if (appName.contains("learn") || appName.contains("study") ||
            appName.contains("course") || appName.contains("quiz") ||
            appName.contains("dictionary") || appName.contains("translate") ||
            appName.contains("school") || appName.contains("academy")) {
            return AppLibraryCategory.EDUCATION
        }

        return null
    }

    private fun getCategoryByAppInfoCategory(
        category: Int,
        appName: String,
        packageName: String
    ): AppLibraryCategory? {
        return when (category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_VIDEO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_GAME -> AppLibraryCategory.GAMES
            ApplicationInfo.CATEGORY_IMAGE -> AppLibraryCategory.CREATIVITY
            ApplicationInfo.CATEGORY_MAPS -> AppLibraryCategory.TRAVEL
            ApplicationInfo.CATEGORY_NEWS -> AppLibraryCategory.INFORMATION_READING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> {
                // Disambiguate finance vs productivity
                if (appName.contains("bank") || appName.contains("pay") ||
                    appName.contains("wallet") || appName.contains("money") ||
                    appName.contains("finance") || packageName.contains("bank") ||
                    packageName.contains("pay") || packageName.contains("wallet")
                ) {
                    AppLibraryCategory.FINANCE
                } else {
                    AppLibraryCategory.PRODUCTIVITY
                }
            }
            ApplicationInfo.CATEGORY_SOCIAL -> AppLibraryCategory.SOCIAL
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppLibraryCategory.UTILITIES
            else -> null
        }
    }
}

