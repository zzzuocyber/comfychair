@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package sh.hnet.comfychair.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.settings.SettingsGroup
import sh.hnet.comfychair.ui.components.settings.SettingsSwitchRow
import sh.hnet.comfychair.ui.components.settings.SettingsTextRow

/** Translation settings: a switch plus three editable fields, all writing to AppSettings. */
@Composable
fun TranslationSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppSettings.isTranslationEnabled(context)) }
    var baseUrl by remember { mutableStateOf(AppSettings.getTranslateBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(AppSettings.getTranslateApiKey(context)) }
    var model by remember { mutableStateOf(AppSettings.getTranslateModel(context)) }

    var editing by remember { mutableStateOf<EditTarget?>(null) }

    SettingsGroup(title = stringResource(R.string.title_translation_settings)) {
        row { shapes ->
            SettingsSwitchRow(
                shapes = shapes,
                title = stringResource(R.string.label_translation_enabled),
                supporting = stringResource(R.string.desc_translation_enabled),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    AppSettings.setTranslationEnabled(context, it)
                }
            )
        }
        row { shapes ->
            SettingsTextRow(
                shapes = shapes,
                title = stringResource(R.string.label_translate_base_url),
                supporting = baseUrl.ifEmpty { stringResource(R.string.translate_not_set) }
            ) { editing = EditTarget.BASE_URL }
        }
        row { shapes ->
            SettingsTextRow(
                shapes = shapes,
                title = stringResource(R.string.label_translate_api_key),
                supporting = if (apiKey.isEmpty()) stringResource(R.string.translate_not_set)
                             else stringResource(R.string.translate_key_set)
            ) { editing = EditTarget.API_KEY }
        }
        row { shapes ->
            SettingsTextRow(
                shapes = shapes,
                title = stringResource(R.string.label_translate_model),
                supporting = model.ifEmpty { stringResource(R.string.translate_not_set) }
            ) { editing = EditTarget.MODEL }
        }
    }

    editing?.let { target ->
        val (title, initial, isSecret) = when (target) {
            EditTarget.BASE_URL -> Triple(stringResource(R.string.label_translate_base_url), baseUrl, false)
            EditTarget.API_KEY -> Triple(stringResource(R.string.label_translate_api_key), apiKey, true)
            EditTarget.MODEL -> Triple(stringResource(R.string.label_translate_model), model, false)
        }
        var value by remember(target) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        placeholder = {
                            Text(
                                when (target) {
                                    EditTarget.BASE_URL -> AppSettings.DEFAULT_TRANSLATE_BASE_URL
                                    EditTarget.MODEL -> AppSettings.DEFAULT_TRANSLATE_MODEL
                                    else -> ""
                                }
                            )
                        }
                    )
                    if (target == EditTarget.BASE_URL) {
                        Text(
                            stringResource(R.string.desc_translate_base_url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        EditTarget.BASE_URL -> {
                            baseUrl = value
                            AppSettings.setTranslateBaseUrl(context, value)
                        }
                        EditTarget.API_KEY -> {
                            apiKey = value
                            AppSettings.setTranslateApiKey(context, value)
                        }
                        EditTarget.MODEL -> {
                            model = value
                            AppSettings.setTranslateModel(context, value)
                        }
                    }
                    editing = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

private enum class EditTarget { BASE_URL, API_KEY, MODEL }
