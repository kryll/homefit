package com.kryll.homefit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kryll.homefit.data.Routine
import com.kryll.homefit.data.ScheduledSession
import com.kryll.homefit.data.WorkoutSession
import com.kryll.homefit.ui.theme.HomeFitTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PROTOTIPO visual (solo capturas, no integrado en la app): franja compacta
 * de la semana lun-dom con entreno/descanso diferenciados y detalle del día
 * seleccionado debajo. Caso sembrado = semana de José: Lun 2 hecho, Mié 4
 * HIIT 20 min (hoy), Vie 6 planificado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekStripPrototypeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ES = Locale("es", "ES")
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 4) // miércoles
    private val routines = listOf(
        Routine(1, "Fuerza en casa", "tonificar", "principiante", "fuerza", false),
        Routine(2, "HIIT", "resistencia", "intermedio", "cardio", false),
    )
    private fun ms(d: LocalDate, h: Int = 18) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val scheduled = listOf(
        ScheduledSession(1, 1, ms(today.minusDays(2)), "COMPLETED"),
        ScheduledSession(2, 2, ms(today), "SCHEDULED"),
        ScheduledSession(3, 1, ms(today.plusDays(2)), "SCHEDULED"),
    )
    private val workouts = listOf(
        WorkoutSession(1, 1, ms(today.minusDays(2), 8), ms(today.minusDays(2), 9), 3600),
    )

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private enum class DayState { DONE, SCHEDULED, REST }

    @Composable
    private fun WeekStripPrototype(selected: LocalDate) {
        val weekStart = com.kryll.homefit.domain.CalendarPeriod.weekStart(today)
        val days = (0 until 7).map { weekStart.plusDays(it.toLong()) }
        val byDay = scheduled.groupBy { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }
        val doneDays = workouts.map { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }.toSet()
        fun stateOf(d: LocalDate) = when {
            d in doneDays || byDay[d]?.any { it.status == "COMPLETED" } == true -> DayState.DONE
            byDay[d]?.any { it.status == "SCHEDULED" } == true -> DayState.SCHEDULED
            else -> DayState.REST
        }
        val sel = selected
        val selSession = byDay[sel]?.firstOrNull()
        val selRoutine = routines.firstOrNull { it.id == selSession?.routineId }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tu semana", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Lun ${weekStart.dayOfMonth} - Dom ${weekStart.plusDays(6).dayOfMonth} ${weekStart.month.getDisplayName(TextStyle.SHORT, ES)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { d ->
                        val state = stateOf(d)
                        val isToday = d == today
                        val isSel = d == sel
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                )
                                .border(
                                    width = if (isToday) 2.dp else 0.dp,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                d.dayOfWeek.getDisplayName(TextStyle.NARROW, ES).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                when (state) {
                                    DayState.DONE -> Text("✓", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
                                    DayState.SCHEDULED -> Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    DayState.REST -> Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● Entreno", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("✓ Hecho", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("· Descanso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Detalle del día seleccionado
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val dow = sel.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).replaceFirstChar { it.uppercase() }
                        val suffix = if (sel == today) " · hoy" else ""
                        Text("$dow ${sel.dayOfMonth} ${sel.month.getDisplayName(TextStyle.SHORT, ES)}$suffix", style = MaterialTheme.typography.titleMedium)
                        when {
                            sel in doneDays || selSession?.status == "COMPLETED" ->
                                Text("${selRoutine?.name ?: "Entreno"} · Hecho", color = MaterialTheme.colorScheme.tertiary)
                            selSession?.status == "SCHEDULED" ->
                                Text("${selRoutine?.name ?: "Entreno"} · 20 min", color = MaterialTheme.colorScheme.primary)
                            else -> Text("Descanso", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    @Test fun stripWithTodaySelected() {
        rule.setContent { HomeFitTheme { WeekStripPrototype(today) } }
        capture("proto_strip_hoy")
    }

    @Test fun stripWithFutureDaySelected() {
        rule.setContent { HomeFitTheme { WeekStripPrototype(today.plusDays(2)) } }
        capture("proto_strip_vie")
    }
}
