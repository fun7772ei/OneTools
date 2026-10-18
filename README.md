# OneTools

Samsung Galaxy（Android 11以降）向けの、Kotlin / Jetpack Compose製Androidツールアプリです。アプリIDは `dev.onetools`。Samsung Electronicsとは提携・関連していない非公式アプリです。

現在の版は **1.10.0 / versionCode 19** です。[最新APKと変更内容](https://github.com/fun7772ei/OneTools/releases/latest)から取得できます。公開版1.9.0以降は「情報」→「アップデートを確認」から更新できます。

## 機能

- バッテリー残量・温度・電圧・充電状態と、OSの消費集計から求めた使用時間の目安。
- Samsungの推定健康度BSOH、Android SOH、ASOC、推定サイクル数。取得できない値は不明として表示します。
- 詳細な記録・充電保護・充電値は折りたたんで表示。項目の長押しで意味や表示方法を確認できます。
- Shizukuの接続案内と、Samsungのシャッター音強制設定の確認・変更。変更結果は端末から再取得します。
- 型番と販売元に対応するSamsung公式の端末更新履歴へのリンク。
- 下引き更新、タブの左右スワイプ、ライト／ダークとAndroid 12以降の壁紙配色。

機種・One UI・地域によって利用できる情報や機能が異なります。健康度や残り時間は報告値・推定値です。シャッター音の強制を解除する設定値にしても、実際の撮影音はカメラアプリ・地域・マナーモードなどに依存します。

## 使い方

1. 初期案内を確認し、Shizukuを準備します。接続は後から設定することもできます。
2. 「接続」でShizukuを開始し、OneToolsへの権限を許可します。
3. 「バッテリー」で情報を更新します。表示は取得した時点の値です。
4. カメラ設定と端末の更新履歴は「ツール」、アプリの更新は「情報」にあります。

## ビルド

JDK 25、Android SDK（platforms;android-37.0 / build-tools;37.0.0、Command-line Tools 23以降）を用意し、SDKの場所を `ANDROID_HOME` または非公開の `local.properties` に設定してください。Gradle Wrapper 9.3.1 / AGP 9.1.1を使用します。minSdk 30 / targetSdk 36 / compileSdk 37です。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Windowsでは `./gradlew.bat` を使います。Debug APKは `app/build/outputs/distribution/debug/OneTools-1.10.0.apk` です。Debug版は公開版と署名が異なるため、公開版への上書き更新には使えません。

公開用ビルドは既存の署名鍵を使い、`./scripts/Build-Release.ps1` で生成します。鍵がない場合は停止します。独自にビルドする場合は自分の鍵を別の非公開フォルダに用意してください。公開配布APKと同じ署名を生成するための秘密鍵は提供しません。

## ソース同期と公開

1.10.0から、ソース・ビルド設定・単体テスト・一部のUIテスト・公開用スクリプトを公開しています。

`./scripts/Publish-Release.ps1` でビルド、安全検査、公開用ソースコピーのビルド、GitHubへの同期、Actions検証、署名済みAPKと更新情報のRelease公開まで実行します。詳しくは[ソースの公開と更新](docs/PUBLIC_SOURCE.md)を参照してください。鍵・ローカル設定・実機ログ・画像などは公開対象に含めません。

Actionsでは安全検査・Debugビルド・単体テスト・Lint・端末テストのコンパイルを行います。端末上でのUI・Shizuku動作を確認した結果とは区別してください。

## 構成

- `app/src/main/java/dev/onetools/`: 画面、状態管理、Shizuku、バッテリー解析、GitHub経由のアプリ更新。
- `app/src/main/aidl/`: Shizuku UserServiceの固定操作。
- `app/src/main/assets/`: Samsung公式更新履歴への型番対応データ。
- `app/src/test/` / `app/src/androidTest/`: 単体テストとUIテスト。
- `scripts/`: 署名ビルド、安全なソース書き出し、Release公開。

## データとライセンス

取得した端末情報は端末内で処理し、実行ログはメモリ内に保持します。初期セットアップの完了状態と販売元選択を端末内に保存します。バッテリー履歴の継続記録は行いません。アプリの更新確認・ダウンロードを操作したときにGitHubへ接続します。端末情報や実行ログを送信しません。

Shizuku APIはMITライセンスです。ライセンス文は `app/src/main/res/raw/shizuku_license.txt` とアプリ内で閲覧できます。Gradle Wrapperにはファイル内に記載されたライセンスが適用されます。本リポジトリの公開自体によって、OneTools独自コードの新たな利用許諾を設定するものではありません。
