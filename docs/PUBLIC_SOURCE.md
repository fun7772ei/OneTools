# ソースの公開と更新

公開先は `fun7772ei/OneTools`。1.10.0からアプリのソース・ビルド設定・テスト・公開用スクリプトもGitHubで管理します。

## 次回以降の公開

1. `app/build.gradle.kts` のversionCodeとversionNameを更新し、`docs/RELEASE_NOTES.md` を今回の内容に書き換えます。
2. 新しい公開ファイルがある場合は `scripts/public-source-files.json` に追加して内容を確認します。新規ファイルを自動的に公開対象へ追加する処理はありません。
3. Android SDK・JDK 25・Python 3.12以降・Git・Git Credential Managerを用意します。既存の公開用署名鍵はプロジェクト外で管理します。
4. PowerShellで `./scripts/Publish-Release.ps1` を実行します。

この1回の操作で、署名済みReleaseのビルド・単体テスト・Lint、公開対象だけを取り出したコピーからのDebugビルド・単体テスト・Lint・端末テストのコンパイル、秘密情報の検査、ソースのコミットと同期、GitHub Actionsの成功待ち、Releaseの3ファイル添付と公開を実行します。

端末での操作試験は自動公開コマンドに含みません。UIや権限処理を変更した場合は公開前に対象端末でも確認します。`-PrepareOnly` でPC内の検査と候補の作成まで進め、端末確認後に `-PublishPrepared` で同じ候補を公開できます。途中でソースやAPKが変わった場合は公開を停止します。

GitHub ActionsはPC内の未送信ファイルを読めません。同期は公開コマンド内で自動実行し、送信後はActionsが公開ソースを再検査します。常駐監視で編集途中のファイルを公開する構成ではありません。

## 公開するもの

- `app/src/main/` のKotlin・AIDL・Manifest・リソース・組み込みデータ。
- 単体テストと、個人の端末記録に依存しない一部のUIテスト。
- Gradle設定・Wrapper・公開用スクリプト・この文書・README・Releaseノート。
- 実際のファイル集合は `scripts/public-source-files.json` に固定しています。特定端末の実測値を固定した旧検証コードと内部の調査資料は対象外です。

## 公開を止める検査

`public_source.py` は許可されていないファイル、リンク、鍵・証明書の秘密部分、一般的な認証トークン、認証情報入りURL、ユーザーディレクトリ、端末シリアルに該当する内容を検出すると停止します。検出内容そのものをログへ出しません。Gitへ追加した内容もインデックスから再検査します。除外設定を無視して追加したファイルも、公開一覧と異なれば拒否します。

Gradle Wrapper JARは公式SHA-256との一致を要求し、Gradle本体にもダウンロード時のSHA-256検証を指定しています。ActionsはコミットSHAで固定し、権限を `contents: read` に制限しています。PRのコードに秘密情報や書き込み権限を渡す `pull_request_target` は使いません。

自動検査ですべての機密情報を判断できるわけではありません。公開一覧を広げるときはファイルの内容も確認してください。ローカルの鍵・署名設定・SDK設定・キャッシュ・成果物・ログ・スクリーンショットは公開コピーへ取り込みません。

## APKとアプリ内更新

署名は既存の公開用鍵でPC内だけで行います。鍵やパスワードをGitHub Secretsへ登録する必要はありません。公開前にAPKの署名証明書・アプリID・版番号・整列・SHA-256を確認します。

ReleaseタグはActionsが成功したソースコミットを参照します。従来と同じ `OneTools-<version>.apk`、`update.json`、`SHA256SUMS.txt` を添付し、添付後のサイズとSHA-256を照合してから正式公開します。既存のRelease・タグ・APKを上書きしません。アプリ内更新が参照するリポジトリ、メタデータ形式、証明書を維持します。

## 参照

- [GitHub Actionsの安全な利用](https://docs.github.com/en/actions/reference/security/secure-use)
- [Actionsを実行するイベント](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [Gradle Wrapperの検証](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification)
