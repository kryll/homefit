package com.kryll.homefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kryll.homefit.R
import com.kryll.homefit.data.UserProfile
import com.kryll.homefit.domain.Bmi
import com.kryll.homefit.domain.ProfileInput

val EQUIPMENT = listOf("mancuernas", "banda_elastica", "banco", "barra_dominadas", "kettlebell", "silla", "comba", "barra_paralelas")
private val GOALS = listOf("salud", "perder_peso", "hipertrofia", "fuerza")
private val LEVELS = listOf("principiante", "intermedio", "avanzado")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: (UserProfile, Set<String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var bDay by rememberSaveable { mutableStateOf("") }
    var bMonth by rememberSaveable { mutableStateOf("") }
    var bYear by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var days by rememberSaveable { mutableStateOf(0b0010101) }
    var goal by rememberSaveable { mutableStateOf(GOALS[0]) }
    var level by rememberSaveable { mutableStateOf(LEVELS[0]) }
    var equipment by rememberSaveable { mutableStateOf(setOf<String>()) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val h = height.replace(',', '.').toFloatOrNull()
    val w = weight.replace(',', '.').toFloatOrNull()
    val birth = ProfileInput.birthEpochDay(bDay.toIntOrNull(), bMonth.toIntOrNull(), bYear.toIntOrNull())
    val errors = ProfileInput.validate(name, h, w, birth, days, minutes.toIntOrNull())
    val bmi = if (h != null && w != null) Bmi.compute(w, h) else null

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.health_disclaimer), style = MaterialTheme.typography.bodySmall)
        field(R.string.onb_name, name, { name = it }, submitted && ProfileInput.Field.NAME in errors, KeyboardType.Text)
        Text(stringResource(R.string.onb_birth))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val e = submitted && ProfileInput.Field.BIRTH in errors
            OutlinedTextField(bDay, { bDay = it.take(2) }, label = { Text(stringResource(R.string.onb_day)) }, isError = e, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(bMonth, { bMonth = it.take(2) }, label = { Text(stringResource(R.string.onb_month)) }, isError = e, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(bYear, { bYear = it.take(4) }, label = { Text(stringResource(R.string.onb_year)) }, isError = e, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.4f))
        }
        field(R.string.onb_height, height, { height = it }, submitted && ProfileInput.Field.HEIGHT in errors, KeyboardType.Decimal)
        field(R.string.onb_weight, weight, { weight = it }, submitted && ProfileInput.Field.WEIGHT in errors, KeyboardType.Decimal)
        if (bmi != null) {
            Text(stringResource(R.string.onb_bmi, bmi, ProfileInput.bmiCategory(bmi)))
        }
        Text(stringResource(R.string.onb_goal))
        chips(GOALS, setOf(goal)) { goal = it }
        Text(stringResource(R.string.onb_level))
        chips(LEVELS, setOf(level)) { level = it }
        Text(stringResource(R.string.onb_days))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                FilterChip(selected = days and (1 shl i) != 0, onClick = { days = days xor (1 shl i) }, label = { Text(d) })
            }
        }
        field(R.string.onb_minutes, minutes, { minutes = it }, submitted && ProfileInput.Field.MINUTES in errors, KeyboardType.Number)
        Text(stringResource(R.string.onb_equipment))
        chips(EQUIPMENT, equipment) { e -> equipment = if (e in equipment) equipment - e else equipment + e }
        Button(
            onClick = {
                submitted = true
                if (errors.isEmpty()) {
                    onDone(
                        UserProfile(
                            name = name.trim(), sex = "", birthEpochDay = birth!!, heightCm = h!!, weightKg = w!!,
                            goal = goal, level = level, daysMask = days, minutesPerSession = minutes.toInt(),
                        ),
                        equipment,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onb_start)) }
    }
}

@Composable
private fun field(label: Int, value: String, onChange: (String) -> Unit, error: Boolean, type: KeyboardType) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) }, isError = error,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun chips(options: List<String>, selected: Set<String>, onClick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o in selected, onClick = { onClick(o) }, label = { Text(o.replace('_', ' ')) }) }
    }
}
