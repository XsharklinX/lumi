@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumi.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumi.galeria.data.Note
import com.lumi.galeria.data.cleanTag

/** Una etiqueta como pastilla: «#familia». Con [onClick] se puede tocar. */
@Composable
fun TagChip(tag: String, on: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        "#$tag", style = LabelStyle.copy(fontSize = 13.sp), color = if (on) Lumi.OnAccent else Lumi.Accent,
        modifier = (if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier).clip(CircleShape).background(if (on) Lumi.Accent else Lumi.Accent.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/**
 * Escribir una nota y poner etiquetas a una foto. Las etiquetas que ya has usado salen debajo para
 * ponerlas con un toque; después, el buscador las encuentra («#familia» o «familia»).
 */
@Composable
fun NoteDialog(note: Note?, usedTags: List<String>, onSave: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(note?.text.orEmpty()) }
    var tags by remember { mutableStateOf(note?.tags.orEmpty()) }
    var typing by remember { mutableStateOf("") }

    fun addTyped() {
        val t = cleanTag(typing)
        if (t.isNotEmpty() && t !in tags) tags = tags + t
        typing = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Nota y etiquetas", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    text, { text = it.take(500) }, placeholder = { Text("Una nota sobre esta foto…") },
                    shape = RoundedCornerShape(16.dp), minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                )
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> TagChip(tag, on = true) { tags = tags - tag } }
                }
                OutlinedTextField(
                    typing, { typing = it.take(24) }, placeholder = { Text("Añadir etiqueta") }, singleLine = true, shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addTyped() }),
                    trailingIcon = {
                        if (typing.isNotBlank()) Text("Añadir", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable { addTyped() }.padding(10.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = usedTags.filter { it !in tags }.take(10)
                if (suggestions.isNotEmpty()) {
                    Text("Las que ya has usado", style = SmallStyle)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { tag -> TagChip(tag) { tags = tags + tag } }
                    }
                }
                Text("Solo se guardan en tu teléfono, no dentro del archivo.", style = SmallStyle)
            }
        },
        confirmButton = {
            Text("Guardar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable {
                val last = cleanTag(typing)
                onSave(text, if (last.isNotEmpty() && last !in tags) tags + last else tags)
            }.padding(12.dp))
        },
        dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}

/** Poner las mismas etiquetas a varias fotos a la vez. */
@Composable
fun TagsDialog(count: Int, usedTags: List<String>, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var typing by remember { mutableStateOf("") }
    fun addTyped() {
        val t = cleanTag(typing)
        if (t.isNotEmpty() && t !in tags) tags = tags + t
        typing = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Etiquetar $count", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> TagChip(tag, on = true) { tags = tags - tag } }
                }
                OutlinedTextField(
                    typing, { typing = it.take(24) }, placeholder = { Text("Por ejemplo: reformas") }, singleLine = true, shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addTyped() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = usedTags.filter { it !in tags }.take(10)
                if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { tag -> TagChip(tag) { tags = tags + tag } }
                }
            }
        },
        confirmButton = {
            Text("Etiquetar", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable {
                val last = cleanTag(typing)
                onAdd(if (last.isNotEmpty() && last !in tags) tags + last else tags)
            }.padding(12.dp))
        },
        dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}

/** Elegir una fecha en un calendario. [start] es el día que se enseña al abrirlo (milisegundos). */
@Composable
fun DatePickDialog(start: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    // El selector trabaja en UTC: se le pasa el mediodía del día local como si fuera UTC.
    val zone = java.time.ZoneId.systemDefault()
    val local = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val state = rememberDatePickerState(initialSelectedDateMillis = local.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text("Poner esta fecha", style = LabelStyle, color = Lumi.Accent, modifier = Modifier.clip(CircleShape).clickable {
                state.selectedDateMillis?.let { utc ->
                    val day = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onPick(day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
                }
            }.padding(12.dp))
        },
        dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    ) { DatePicker(state) }
}

/** Cuánto tardan en borrarse solas las fotos temporales. */
@Composable
fun TemporaryDialog(count: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lumi.Surface,
        title = { Text("Borrar sola dentro de…", style = HeadingStyle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (count == 1) "La foto pasará a la papelera cuando acabe el plazo, donde sigue 30 días." else "Las $count fotos pasarán a la papelera cuando acabe el plazo, donde siguen 30 días.",
                    style = SmallStyle.copy(fontSize = 14.sp, color = Lumi.Ink),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 día", 3 to "3 días", 7 to "1 semana", 30 to "1 mes").forEach { (days, label) ->
                        Text(
                            label, style = LabelStyle, color = Lumi.OnAccent,
                            modifier = Modifier.clip(CircleShape).background(Lumi.Accent).clickable { onPick(days) }.padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { Text("Cancelar", style = LabelStyle, color = Lumi.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp)) },
    )
}
