package dev.bessaract.kassa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.bessaract.kassa.ui.BackupUi
import dev.bessaract.kassa.ui.SpendingState
import dev.bessaract.kassa.ui.components.GhostButton
import dev.bessaract.kassa.ui.components.PrimaryButton
import dev.bessaract.kassa.ui.theme.KassaColors
import dev.bessaract.kassa.ui.theme.KassaType
import dev.bessaract.kassa.ui.theme.distinctButtonVariants

/**
 * Лист «Настройки» (⚙ в служебной строке). Пока в нём только резервная копия:
 * «Сохранить копию» — файл JSON туда, куда выберет пользователь; «Восстановить из копии» — объединением.
 */
@Composable
fun SettingsSheetContent(
    s: SpendingState,
    backup: BackupUi,
    onExport: () -> Unit,
    onPick: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy = backup == BackupUi.Busy
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("НАСТРОЙКИ", style = KassaType.SheetTitle, modifier = Modifier.weight(1f))
            Text("設定", style = KassaType.cjk(14).copy(color = KassaColors.Lime))
        }
        SheetLabel("Резервная копия")

        if (backup is BackupUi.Ready) {
            val p = backup.plan
            Info("Копия от ${backup.whenText}. Добавится только то, чего нет в телефоне. Что уже есть, не меняется.")
            StatRow("Новых операций", p.tx.size)
            StatRow("Новых строк плана", p.rec.size)
            StatRow("Новых категорий", p.cats.size)
            StatRow("Уже есть, пропущу", p.skipped)
            Spacer(Modifier.height(16.dp))
            val keys = listOfNotNull(if (p.isEmpty) null else "Добавить из копии⇲⇲", "Отмена取消")
            val v = remember(keys) { distinctButtonVariants(keys) }
            if (p.isEmpty) {
                Info("Всё из этой копии уже есть в телефоне.")
                Spacer(Modifier.height(8.dp))
            } else {
                PrimaryButton("Добавить из копии", variant = v.first(), onClick = onApply)
                Spacer(Modifier.height(8.dp))
            }
            GhostButton("Отмена", "取消", variant = v.last(), onClick = onCancel)
            return@Column
        }

        Info("Один файл со всеми данными: план, категории, операции. Лежит там, куда сохранишь (Загрузки, Диск). Приложение никуда его не отправляет.")
        StatRow("Операций", s.txTotal)
        StatRow("Строк плана", s.rec.size)
        when (backup) {
            is BackupUi.Saved -> Note("Копия сохранена: операций — ${backup.count}.", KassaColors.Lime)
            is BackupUi.Added -> Note("Добавлено: операций — ${backup.tx}, строк плана — ${backup.rec}.", KassaColors.Lime)
            is BackupUi.Error -> Note(backup.message, KassaColors.Bad)
            BackupUi.Busy -> Note("Секунду…", KassaColors.Grey)
            else -> {}
        }
        Spacer(Modifier.height(16.dp))
        val keys = listOf("Сохранить копию⇲⇲", "Восстановить из копии復元", "Отмена取消")
        val v = remember { distinctButtonVariants(keys) }
        PrimaryButton("Сохранить копию", variant = v[0]) { if (!busy) onExport() }
        Spacer(Modifier.height(8.dp))
        GhostButton("Восстановить из копии", "復元", variant = v[1]) { if (!busy) onPick() }
        Spacer(Modifier.height(8.dp))
        GhostButton("Отмена", "取消", variant = v[2], onClick = onCancel)
    }
}

@Composable
private fun Info(text: String) {
    Text(text, style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f), modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = KassaType.Small.copy(color = color), modifier = Modifier.padding(top = 10.dp))
}

/** Строка «подпись … число» с линией снизу (.stat-row). */
@Composable
private fun StatRow(label: String, value: Int) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(KassaColors.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = 8.dp),
    ) {
        Text(label, style = KassaType.Small.copy(fontSize = KassaType.Body.fontSize * 0.92f), modifier = Modifier.weight(1f))
        Text(value.toString(), style = KassaType.Body.copy(fontSize = KassaType.Body.fontSize * 0.92f))
    }
}
