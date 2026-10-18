package dev.onetools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun InformationPage(onSetup: (() -> Unit)? = null, onLicense: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = scheme.primaryContainer) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                ToolGlyph(5, scheme.primary, Modifier.size(38.dp))
            }
        }
        Text("OneTools", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("バージョン ${BuildConfig.VERSION_NAME}", color = scheme.onSurfaceVariant, fontSize = 13.sp)
    }
    InformationCard("OneToolsについて") {
        Text("Galaxyのバッテリー情報や便利な設定を、コマンド入力なしで確認・操作できるアプリです。", fontSize = 15.sp)
        Text("バッテリー", fontWeight = FontWeight.Medium)
        Text("健康度の報告値、推定サイクル数、温度、充電保護などを確認できます。項目を長押しすると、意味や計算方法を表示します。", fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Text("ツール", fontWeight = FontWeight.Medium)
        Text("Samsungのカメラのシャッター音強制設定を確認・変更できます。撮影音の変化はカメラや地域などにも依存します。", fontSize = 13.sp, color = scheme.onSurfaceVariant)
    }
    InformationCard("アップデート") {
        AppUpdateCard()
    }
    InformationCard("使いはじめるには") {
        Text("「接続」タブからShizukuを準備し、OneToolsに権限を許可してください。端末の再起動などでShizukuが停止した場合は、再び開始して接続を確認してください。", fontSize = 14.sp)
        if (onSetup != null) TextButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("初期セットアップを表示") }
        Text("Galaxy向け · Android 11以降", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text("利用できる情報や機能は、機種・One UI・地域によって異なります。取得できない値は不明として表示します。", fontSize = 13.sp, color = scheme.onSurfaceVariant)
    }
    InformationCard("データとアプリの情報") {
        Text("取得した情報は端末内で処理します。バッテリー履歴の継続記録は行いません。実行ログは通常利用時にはメモリ内だけに保持します。", fontSize = 14.sp)
        Text(stringResource(R.string.unofficial_notice), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        TextButton(onClick = onLicense, modifier = Modifier.fillMaxWidth()) { Text("Shizukuのライセンス") }
    }
}

@Composable private fun InformationCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, Modifier.padding(start=20.dp, top=8.dp), fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
    }
}
