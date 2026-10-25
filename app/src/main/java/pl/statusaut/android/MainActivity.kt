package pl.statusaut.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Blue = Color(0xFF0071EB)
private val Green = Color(0xFF4C9A2A)
private val Page = Color(0xFFF4F8FF)
private val Ink = Color(0xFF054752)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StatusAutApp() }
    }
}

data class Car(
    val id: String,
    val name: String,
    val plate: String,
    val occupied: Boolean,
    val by: String,
    val phone: String,
    val time: String,
    val caretaker: String
)

private object Api {
    fun request(base: String, path: String, method: String = "GET", body: JSONObject? = null): String {
        require(base.startsWith("https://")) { "Adres API musi zaczynać się od https://" }
        val cleanBase = base.trimEnd('/') + "/"
        val connection = (URL(cleanBase + path.trimStart('/')).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Błąd serwera HTTP $status")
        }
        return response
    }

    fun cars(base: String): List<Car> {
        val json = JSONObject(request(base, "api.php"))
        return json.keys().asSequence().map { id ->
            val c = json.getJSONObject(id)
            Car(
                id = id,
                name = c.optString("name", id),
                plate = c.optString("plate"),
                occupied = c.optBoolean("occupied"),
                by = c.optString("by"),
                phone = c.optString("phone"),
                time = c.optString("time"),
                caretaker = c.optString("caretaker")
            )
        }.toList()
    }

    fun postCarAction(base: String, carId: String, action: String, fields: Map<String, String>, boardCode: String) {
        val json = JSONObject()
        json.put("id", carId)
        json.put("action", action)
        json.put("board_code", boardCode)
        fields.forEach { (key, value) -> json.put(key, value) }
        request(base, "api.php", "POST", json)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusAutApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("status_aut", 0) }
    var baseUrl by remember { mutableStateOf(prefs.getString("api_base_url", "") ?: "") }
    var boardCode by remember { mutableStateOf(prefs.getString("board_code", "") ?: "") }
    var tab by remember { mutableIntStateOf(0) }
    var cars by remember { mutableStateOf<List<Car>>(emptyList()) }
    var reservations by remember { mutableStateOf("Nie pobrano rezerwacji.") }
    var stats by remember { mutableStateOf("Nie pobrano statystyk.") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(baseUrl.isBlank()) }
    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var action by remember { mutableStateOf("take") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadCars() {
        if (baseUrl.isBlank()) { settingsOpen = true; return }
        loading = true
        error = null
        scope.launch {
            try {
                cars = withContext(Dispatchers.IO) { Api.cars(baseUrl) }
                info = "Dane aut zostały odświeżone."
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się pobrać danych."
            } finally { loading = false }
        }
    }

    fun loadReservations() {
        if (baseUrl.isBlank()) { settingsOpen = true; return }
        loading = true; error = null
        scope.launch {
            try {
                reservations = withContext(Dispatchers.IO) { Api.request(baseUrl, "reservations.php") }
            } catch (e: Exception) { error = e.message ?: "Nie udało się pobrać rezerwacji." }
            finally { loading = false }
        }
    }

    fun loadStats() {
        if (baseUrl.isBlank()) { settingsOpen = true; return }
        loading = true; error = null
        scope.launch {
            try { stats = withContext(Dispatchers.IO) { Api.request(baseUrl, "stats.php") } }
            catch (e: Exception) { error = e.message ?: "Nie udało się pobrać statystyk." }
            finally { loading = false }
        }
    }

    LaunchedEffect(baseUrl, tab) {
        if (baseUrl.isNotBlank()) {
            when (tab) {
                0 -> loadCars()
                1 -> loadReservations()
                2 -> loadStats()
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Green,
            background = Page,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Status aut firmowych", fontWeight = FontWeight.ExtraBold)
                            Text(
                                when (tab) { 0 -> "Wolne i zajęte pojazdy"; 1 -> "Planer rezerwacji"; 2 -> "Statystyki użycia"; else -> "Ustawienia" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                        }
                        TextButton(onClick = {
                            when (tab) { 0 -> loadCars(); 1 -> loadReservations(); 2 -> loadStats() }
                        }) { Text("Odśwież") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Page)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                        label = { Text("Auta") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Event, contentDescription = null) },
                        label = { Text("Planer") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.QueryStats, contentDescription = null) },
                        label = { Text("Statystyki") }
                    )
                }
            },
            containerColor = Page
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> CarsScreen(cars, loading, onCarClick = { car ->
                        selectedCar = car
                        action = if (car.occupied) "free" else "take"
                        name = ""; phone = ""; code = ""
                    })
                    1 -> JsonScreen(
                        title = "Rezerwacje",
                        content = reservations,
                        loading = loading,
                        hint = "To pierwsza wersja ekranu planera. Dane są pobierane z reservations.php; formularze rezerwacji będą kolejnym etapem."
                    )
                    2 -> JsonScreen(
                        title = "Statystyki",
                        content = stats,
                        loading = loading,
                        hint = "Dane pochodzą z stats.php. W kolejnym etapie można zamienić surowy JSON na natywne wykresy i zestawienia."
                    )
                }
                if (error != null) {
                    AlertDialog(
                        onDismissRequest = { error = null },
                        title = { Text("Nie udało się wykonać operacji") },
                        text = { Text(error ?: "") },
                        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }
                    )
                }
                if (info != null) {
                    LaunchedEffect(info) {
                        kotlinx.coroutines.delay(2200)
                        info = null
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { if (baseUrl.isNotBlank()) settingsOpen = false },
            title = { Text("Połączenie z serwerem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Wpisz adres katalogu na serwerze, w którym znajdują się api.php i pozostałe pliki PHP.")
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Adres HTTPS, np. https://twoja-domena.pl/auta/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = boardCode,
                        onValueChange = { boardCode = it },
                        label = { Text("Wspólny kod tablicy (jeśli wymagany)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Adres musi używać HTTPS. Nie wpisuj tutaj hasła administratora ani danych bazy MySQL.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit().putString("api_base_url", baseUrl.trim()).putString("board_code", boardCode).apply()
                    settingsOpen = false
                    when (tab) { 0 -> loadCars(); 1 -> loadReservations(); 2 -> loadStats() }
                }) { Text("Zapisz i połącz") }
            },
            dismissButton = {
                if (baseUrl.isNotBlank()) TextButton(onClick = { settingsOpen = false }) { Text("Anuluj") }
            }
        )
    }

    if (selectedCar != null) {
        val car = selectedCar!!
        AlertDialog(
            onDismissRequest = { selectedCar = null },
            title = { Text(if (action == "take") "Zajmij auto" else "Zwolnij auto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${car.name}${if (car.plate.isNotBlank()) " · ${car.plate}" else ""}")
                    if (action == "take") {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Imię i nazwisko") }, singleLine = true)
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Numer telefonu") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kod do zwolnienia auta") }, singleLine = true)
                    } else {
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kod do zwolnienia auta") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val required = if (action == "take") name.isNotBlank() && phone.isNotBlank() && code.length >= 4 else code.isNotBlank()
                    if (!required) {
                        error = if (action == "take") "Uzupełnij imię, telefon i kod (minimum 4 znaki)." else "Wpisz kod zwolnienia."
                    } else {
                        val currentCar = car
                        val currentAction = action
                        val fields = if (currentAction == "take") mapOf("by" to name.trim(), "phone" to phone.trim(), "code" to code) else mapOf("code" to code)
                        selectedCar = null
                        loading = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    Api.postCarAction(baseUrl, currentCar.id, currentAction, fields, boardCode)
                                }
                                info = "Operacja zakończona."
                                loadCars()
                            } catch (e: Exception) {
                                error = e.message ?: "Nie udało się wykonać operacji."
                            } finally { loading = false }
                        }
                    }
                }) { Text(if (action == "take") "Zajmij" else "Zwolnij") }
            },
            dismissButton = { TextButton(onClick = { selectedCar = null }) { Text("Anuluj") } }
        )
    }
}

@Composable
private fun CarsScreen(cars: List<Car>, loading: Boolean, onCarClick: (Car) -> Unit) {
    if (cars.isEmpty() && !loading) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Blue, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Brak danych aut", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Otwórz ustawienia i podaj adres serwera PHP.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(cars, key = { it.id }) { car ->
                Card(
                    onClick = { onCarClick(car) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (car.plate.isNotBlank()) Text(car.plate, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(
                                color = if (car.occupied) Color(0xFFEAF3FF) else Color(0xFFEAF7E0),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    if (car.occupied) "ZAJĘTE" else "WOLNE",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (car.occupied) Blue else Green,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (car.caretaker.isNotBlank()) Text("Opiekun kluczyków: ${car.caretaker}", color = Color.Gray)
                        if (car.occupied) {
                            Text("Zajęte przez: ${car.by} · ${car.time}")
                            if (car.phone.isNotBlank()) Text("Telefon: ${car.phone}")
                        }
                        Text(
                            if (car.occupied) "Dotknij, aby zwolnić auto" else "Dotknij, aby zająć auto",
                            color = Blue,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonScreen(title: String, content: String, loading: Boolean, hint: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(hint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(content, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
