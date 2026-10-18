package com.example.reversi.update

import com.example.reversi.update.GitHubReleaseChecker.Companion.isNewer
import com.example.reversi.update.GitHubReleaseChecker.Companion.parseLatest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {

    @Test
    fun comparesVersionsPartByPartAsNumbers() {
        assertTrue(isNewer("1.6", "1.5"))
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("2.0", "1.99"))
        assertTrue(isNewer("1.5.1", "1.5"))
        assertFalse(isNewer("1.5", "1.5"))
        assertFalse(isNewer("1.5.0", "1.5"))
        assertFalse(isNewer("1.4", "1.5"))
    }

    @Test
    fun neverOffersAVersionThatDoesNotParse() {
        assertFalse(isNewer("1.6-beta", "1.5"))
        assertFalse(isNewer("", "1.5"))
        assertFalse(isNewer("1.6", "dev"))
    }

    @Test
    fun readsTheLatestReleaseAndStripsTheTagPrefix() {
        val body = """
            {"tag_name": "v1.6", "name": "Resign and hints",
             "html_url": "https://github.com/HLahoti/ReversiApp/releases/tag/v1.6",
             "draft": false, "assets": [{"name": "Reversi.apk"}]}
        """
        val update = parseLatest(body, installedVersion = "1.5")
        assertEquals(
            AvailableUpdate(
                versionName = "1.6",
                title = "Resign and hints",
                url = "https://github.com/HLahoti/ReversiApp/releases/tag/v1.6",
            ),
            update,
        )
    }

    @Test
    fun reportsNothingWhenUpToDate() {
        assertNull(parseLatest("""{"tag_name": "v1.5"}""", installedVersion = "1.5"))
        assertNull(parseLatest("""{"tag_name": "v1.4"}""", installedVersion = "1.5"))
    }

    @Test
    fun dropsABlankTitle() {
        assertNull(parseLatest("""{"tag_name": "v1.6", "name": ""}""", "1.5")!!.title)
    }
}
