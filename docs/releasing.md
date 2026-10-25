# 配布手順

JDK 21とAndroid SDK 37で、全ソースをコミットした後に実行します。

```sh
python3 tools/package_release.py --source-ref HEAD
```

`tools/package_release.py`はrelease variant、単体試験、lint、ライセンス照合を実行し、非debuggable・対象パッケージとSDK・検証Activity非混入・API 102 metadata・スコープ・16 KiB alignment・署名を検証します。`dist/`に署名済みAPK、対応コミットのソースZIP、SHA256SUMS、署名検証結果を生成します。

鍵はGit対象外の`.signing/release.keystore`、aliasは`sesameui-release`、パスワードは`.signing/password`です。ディレクトリ0700、ファイル0600とします。秘密値をログ・Git・配布物に含めません。

配布証明書SHA-256: `c2020a1338f2504d893b368edfb77b0ab76cedeb8016df42876439a1cc92acd6`

GitHubはOneUI-Mods/DarkAppToolsの公開リポジトリを使用します。CIは署名鍵なしで試験・lint・release構造・依存ライセンスを確認し、APKを自動配布しません。GitHub Releaseには署名済みAPK、ソースZIP、ハッシュ、署名検証結果を添付します。Actionsアーティファクトを残しません。

リリース文は外部向けに使い勝手、対応環境、導入操作、制約を説明します。内部クラス名・試験の経緯・個人の設定値は入れません。タグを移動しません。
