package app.litterguard.core.alerts

import app.litterguard.core.alerts.delivery.AlertCaption
import app.litterguard.core.alerts.delivery.DeliveryOutcome
import app.litterguard.core.alerts.delivery.IncidentDelivery
import app.litterguard.core.alerts.telegram.TelegramClient
import app.litterguard.core.alerts.telegram.TelegramResult
import app.litterguard.core.domain.ports.AlertSetupResult
import app.litterguard.core.domain.site.SiteConfig
import app.litterguard.core.model.DeliveryState
import app.litterguard.core.model.EvidenceFile
import app.litterguard.core.model.EvidenceKind
import app.litterguard.core.model.Incident
import app.litterguard.core.model.IncidentId
import app.litterguard.core.model.IncidentKind
import app.litterguard.core.model.ObjectCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Contract tests against a fake Telegram server; nothing here touches the network. */
abstract class FakeTelegramTest {
    internal val server = MockWebServer()
    internal lateinit var client: TelegramClient

    @BeforeEach
    fun startServer() {
        server.start()
        client = TelegramClient(OkHttpClient(), server.url("/"), Dispatchers.IO)
    }

    @AfterEach
    fun stopServer() = server.close()

    internal fun reply(
        body: String,
        code: Int = 200,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    internal fun ok(result: String) = reply("""{"ok":true,"result":$result}""")

    internal fun error(
        code: Int,
        extra: String = "",
    ) = reply("""{"ok":false,"error_code":$code,"description":"nope"$extra}""", code)
}

class TelegramClientTest : FakeTelegramTest() {
    @Test
    fun `reads the bot's username`() =
        runTest {
            ok("""{"id":1,"is_bot":true,"username":"LitterGuardBot"}""")

            val result = client.getMe(FAKE_TOKEN)

            assertThat((result as TelegramResult.Ok).value.username).isEqualTo("LitterGuardBot")
            assertThat(server.takeRequest().url.encodedPath).endsWith("/getMe")
        }

    @Test
    fun `maps every kind of failure`() =
        runTest {
            error(401)
            error(429, ""","parameters":{"retry_after":7}""")
            reply("<html>gateway</html>", 502)
            error(400, ""","parameters":{"migrate_to_chat_id":-100123}""")
            error(400)

            assertThat(client.sendMessage(FAKE_TOKEN, "-1", "a")).isEqualTo(TelegramResult.BadToken)
            assertThat(client.sendMessage(FAKE_TOKEN, "-1", "b")).isEqualTo(TelegramResult.Retryable(7, 429))
            assertThat(client.sendMessage(FAKE_TOKEN, "-1", "c")).isEqualTo(TelegramResult.Retryable(httpCode = 502))
            assertThat(client.sendMessage(FAKE_TOKEN, "-1", "d")).isEqualTo(TelegramResult.ChatMoved("-100123"))
            assertThat(client.sendMessage(FAKE_TOKEN, "-1", "e")).isEqualTo(TelegramResult.Rejected(400, "nope"))
        }

    @Test
    fun `being offline is retryable and the result never carries the token`() =
        runTest {
            server.close()

            val result = client.sendMessage(FAKE_TOKEN, "-1", "hello")

            assertThat(result).isEqualTo(TelegramResult.Retryable())
            assertThat(result.toString()).doesNotContain(FAKE_TOKEN)
        }

    @Test
    fun `several photos go as one album with a single caption`(
        @TempDir dir: File,
    ) = runTest {
        ok("[]")
        val photos = listOf("a.jpg", "b.jpg").map { File(dir, it).apply { writeText(it) } }

        client.sendPhotos(FAKE_TOKEN, "-1", photos, "the caption")

        val request = server.takeRequest()
        val body = requireNotNull(request.body).utf8()
        assertThat(request.url.encodedPath).endsWith("/sendMediaGroup")
        assertThat(body).contains("attach://photo1")
        assertThat(body.split("the caption")).hasSize(2)
    }

    @Test
    fun `a single photo uses sendPhoto`(
        @TempDir dir: File,
    ) = runTest {
        ok("{}")

        client.sendPhotos(FAKE_TOKEN, "-1", listOf(File(dir, "a.jpg").apply { writeText("x") }), "c")

        assertThat(server.takeRequest().url.encodedPath).endsWith("/sendPhoto")
    }
}

class TelegramSetupTest : FakeTelegramTest() {
    private val credentials = FakeCredentials()
    private val site = FakeSiteConfig()

    private fun channel() = TelegramAlertChannel(client, credentials, site, Dispatchers.IO)

    private fun group(
        updateId: Int,
        text: String,
        chatId: Long = -5,
        title: String = "LitterGuard Test",
    ) = """{"update_id":$updateId,"message":{"chat":{"id":$chatId,"type":"group","title":"$title"},"text":"$text"}}"""

    @Test
    fun `text that isn't a bot token is rejected without calling Telegram`() =
        runTest {
            assertThat(channel().connect("my password")).isEqualTo(AlertSetupResult.InvalidToken)
            assertThat(server.requestCount).isEqualTo(0)
        }

    @Test
    fun `a valid token is stored with the bot's name`() =
        runTest {
            ok("""{"id":1,"username":"LitterGuardBot"}""")

            val result = channel().connect("  $FAKE_TOKEN\n")

            assertThat(result).isEqualTo(AlertSetupResult.Success)
            assertThat(credentials.token()).isEqualTo(FAKE_TOKEN)
            assertThat(credentials.botUsername.value).isEqualTo("LitterGuardBot")
        }

    @Test
    fun `the group that used this setup's link wins over newer ones`() =
        runTest {
            ok("""{"id":1,"username":"LitterGuardBot"}""")
            val channel = channel()
            channel.connect(FAKE_TOKEN)
            val nonce = requireNotNull(channel.addToGroupLink()).substringAfter("startgroup=")
            ok(
                "[" + group(1, "/start@LitterGuardBot $nonce") + "," +
                    group(2, "/start@LitterGuardBot", chatId = -9, title = "Stranger") + "]",
            )

            assertThat(channel.findGroup()).isEqualTo(AlertSetupResult.Success)
            assertThat(site.current.value.alertChatId).isEqualTo("-5")
            assertThat(site.current.value.alertChatTitle).isEqualTo("LitterGuard Test")
        }

    @Test
    fun `being added to a group is enough`() =
        runTest {
            ok("""{"id":1,"username":"LitterGuardBot"}""")
            val channel = channel()
            channel.connect(FAKE_TOKEN)
            ok(
                """[{"update_id":4,"my_chat_member":{"chat":{"id":-7,"type":"supergroup","title":"RWA"},""" +
                    """"new_chat_member":{"status":"member"}}}]""",
            )

            channel.findGroup()

            assertThat(site.current.value.alertChatTitle).isEqualTo("RWA")
        }

    @Test
    fun `private chats, other bots' commands and removals are not groups to alert`() =
        runTest {
            ok("""{"id":1,"username":"LitterGuardBot"}""")
            val channel = channel()
            channel.connect(FAKE_TOKEN)
            ok(
                """[{"update_id":1,"message":{"chat":{"id":3,"type":"private"},"text":"/start"}},""" +
                    group(2, "/start@SomeOtherBot") + "," +
                    """{"update_id":3,"my_chat_member":{"chat":{"id":-7,"type":"group","title":"x"},""" +
                    """"new_chat_member":{"status":"left"}}}]""",
            )

            assertThat(channel.findGroup()).isEqualTo(AlertSetupResult.GroupNotFound)
            assertThat(site.current.value.alertChatId).isNull()
        }

    @Test
    fun `a test alert follows a group that became a supergroup`() =
        runTest {
            ok("""{"id":1,"username":"LitterGuardBot"}""")
            val channel = channel()
            channel.connect(FAKE_TOKEN)
            site.update { it.copy(alertChatId = "-5", alertChatTitle = "g") }
            error(400, ""","parameters":{"migrate_to_chat_id":-100555}""")
            ok("{}")

            assertThat(channel.sendTestAlert("Gate 2")).isEqualTo(AlertSetupResult.Success)
            assertThat(site.current.value.alertChatId).isEqualTo("-100555")
        }
}

class IncidentDeliveryTest : FakeTelegramTest() {
    @TempDir
    lateinit var folder: File

    private val time = Instant.parse("2026-11-27T04:30:15Z")

    private fun incident(state: DeliveryState = DeliveryState.PENDING) =
        Incident(
            id = IncidentId("abcdef12-3456"),
            siteName = "Gate 2",
            kind = IncidentKind.CONFIRMED_DUMPING,
            actorCategories = setOf(ObjectCategory.VEHICLE, ObjectCategory.PERSON),
            actorEnteredAt = time,
            actorLeftAt = time,
            confirmedAt = time,
            objectBox = null,
            deliveryState = state,
            evidence =
                listOf(
                    EvidenceFile(EvidenceKind.ACTOR, "actor.jpg", 1, 1, time),
                    EvidenceFile(EvidenceKind.SCENE, "scene.jpg", 1, 1, time),
                ),
        )

    private val captions = AlertCaption { ZoneId.of("Asia/Kolkata") }

    private fun delivery(
        incidents: FakeIncidents,
        site: FakeSiteConfig = FakeSiteConfig(SiteConfig(alertChatId = "-5")),
    ) = IncidentDelivery(
        client = client,
        credentials = FakeCredentials(FAKE_TOKEN, "bot"),
        site = site,
        incidents = incidents,
        evidence = FolderEvidenceStore(folder),
        captions = captions,
        io = Dispatchers.IO,
    )

    private fun writePhotos() {
        File(folder, "scene.jpg").writeText("SCENE-BYTES")
        File(folder, "actor.jpg").writeText("ACTOR-BYTES")
    }

    @Test
    fun `sends the scene first and marks the incident delivered`() =
        runTest {
            writePhotos()
            ok("[]")
            val incidents = FakeIncidents(incident())

            val outcome = delivery(incidents).deliver(IncidentId("abcdef12-3456"))

            val body = requireNotNull(server.takeRequest().body).utf8()
            assertThat(outcome).isEqualTo(DeliveryOutcome.DONE)
            assertThat(body.indexOf("SCENE-BYTES")).isLessThan(body.indexOf("ACTOR-BYTES"))
            assertThat(incidents.byId("abcdef12-3456").deliveryState).isEqualTo(DeliveryState.DELIVERED)
        }

    @Test
    fun `a delivered incident is never sent again`() =
        runTest {
            val outcome =
                delivery(
                    FakeIncidents(incident(DeliveryState.DELIVERED)),
                ).deliver(IncidentId("abcdef12-3456"))

            assertThat(outcome).isEqualTo(DeliveryOutcome.DONE)
            assertThat(server.requestCount).isEqualTo(0)
        }

    @Test
    fun `a temporary problem is retried and stays pending`() =
        runTest {
            error(503)
            val incidents = FakeIncidents(incident())

            val outcome = delivery(incidents).deliver(IncidentId("abcdef12-3456"))

            assertThat(outcome).isEqualTo(DeliveryOutcome.RETRY)
            assertThat(incidents.byId("abcdef12-3456").deliveryState).isEqualTo(DeliveryState.PENDING)
        }

    @Test
    fun `a rejection or missing setup is a visible failure`() =
        runTest {
            error(403)
            val rejected = FakeIncidents(incident())
            val unset = FakeIncidents(incident())

            delivery(rejected).deliver(IncidentId("abcdef12-3456"))
            delivery(unset, FakeSiteConfig()).deliver(IncidentId("abcdef12-3456"))

            assertThat(rejected.byId("abcdef12-3456").deliveryState).isEqualTo(DeliveryState.FAILED)
            assertThat(unset.byId("abcdef12-3456").deliveryState).isEqualTo(DeliveryState.FAILED)
        }

    @Test
    fun `without photos a text alert still goes out`() =
        runTest {
            ok("{}")

            delivery(FakeIncidents(incident())).deliver(IncidentId("abcdef12-3456"))

            assertThat(server.takeRequest().url.encodedPath).endsWith("/sendMessage")
        }

    @Test
    fun `the caption names the site, local time and who was seen`() {
        assertThat(captions.of(incident()))
            .isEqualTo(
                "🚨 Illegal dumping detected\n" +
                    "Site: Gate 2\n" +
                    "Time: Fri 27 Nov 2026, 10:00:15 AM\n" +
                    "Seen: a person and a vehicle\n" +
                    "Number plate: not read yet, please check the photos\n" +
                    "Ref: abcdef12",
            )
    }
}
