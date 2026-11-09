package com.example.lab1.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab1.AppConstants
import com.example.lab1.helpers.AccessClass
import com.example.lab1.helpers.ScenarioAction
import com.example.lab1.helpers.ScenarioCondition
import com.example.lab1.helpers.describe
import com.example.lab1.repository.ScenarioRule

/** Lab 7: "Scenarios" tab — the user smart rules "IF the light level > X THEN switch the vibration on". */
@Composable
fun ScenariosScreen(vm: ScenariosViewModel, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ScenariosEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    ScenariosContent(state = state, onAction = vm::onAction, modifier = modifier)
}

/** A pure function: State in, Action out. */
@Composable
fun ScenariosContent(state: ScenariosState, onAction: (ScenariosAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart scenarios", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Rules are kept in Room and work without the Internet. Noise filter: a lux rule fires after " +
                "${AppConstants.SCENARIO_CONFIRM_MS} ms and is released ${AppConstants.SCENARIO_HYSTERESIS_LUX} lux " +
                "past its threshold.",
            style = MaterialTheme.typography.bodySmall
        )
        NewRuleCard(state.draft, onAction)
        Text("Rules (${state.rules.size})", style = MaterialTheme.typography.titleMedium)
        if (state.rules.isEmpty()) Text("No rules yet — add the first one above.", color = Color.Gray)
        state.rules.forEach { rule ->
            RuleCard(rule, isActive = rule.id in state.activeRuleIds, hubConnected = state.hubConnected, onAction)
        }
    }
}

@Composable
private fun NewRuleCard(draft: ScenarioDraft, onAction: (ScenariosAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("IF", fontWeight = FontWeight.Bold)
            ChipRow(ScenarioCondition.entries, draft.condition, { it.title }) {
                onAction(ScenariosAction.SetCondition(it))
            }
            if (draft.condition == ScenarioCondition.AI_CLASS) {
                ChipRow(AccessClass.entries, draft.aiClass, { it.name }) { onAction(ScenariosAction.SetAiClass(it)) }
            } else {
                OutlinedTextField(
                    value = draft.thresholdText,
                    onValueChange = { onAction(ScenariosAction.SetThreshold(it)) },
                    label = { Text("Threshold, lux") },
                    isError = draft.threshold == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("THEN", fontWeight = FontWeight.Bold)
            ChipRow(ScenarioAction.entries, draft.action, { it.title }) { onAction(ScenariosAction.SetAction(it)) }
            Button(
                onClick = { onAction(ScenariosAction.AddRule) },
                enabled = draft.isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add rule") }
        }
    }
}

/** One choice out of an enum; the chips wrap to the next line, so every option stays visible. */
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun RuleCard(rule: ScenarioRule, isActive: Boolean, hubConnected: Boolean, onAction: (ScenariosAction) -> Unit) {
    val container = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.describe(), fontWeight = FontWeight.Bold)
                Text(
                    text = if (isActive) "ACTIVE now" else if (rule.enabled) "waiting" else "off",
                    style = MaterialTheme.typography.bodySmall
                )
                if (rule.action == ScenarioAction.BLE_ALARM && rule.enabled && !hubConnected) {
                    Text(
                        text = "BLE hub not connected — connect the lock on the BLE tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { onAction(ScenariosAction.DeleteRule(rule.id)) }) { Text("Delete") }
            }
            Switch(checked = rule.enabled, onCheckedChange = { onAction(ScenariosAction.SetEnabled(rule.id, it)) })
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ScenariosScreenPreview() {
    val rules = listOf(
        ScenarioRule(
            id = 1, condition = ScenarioCondition.LUX_ABOVE, threshold = 10f, action = ScenarioAction.VIBRATION
        ),
        ScenarioRule(id = 2, condition = ScenarioCondition.AI_CLASS, action = ScenarioAction.BLE_ALARM, enabled = false)
    )
    MaterialTheme {
        ScenariosContent(ScenariosState(rules = rules, activeRuleIds = setOf(1L)), onAction = {})
    }
}
