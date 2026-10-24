# 開発者向け資料

## 構成

- パッケージ: `dev.ryuya.sesameui`
- 対象: `co.candyhouse.sesame2` のメインプロセスのみ
- 動作対象の契約: versionName `3.0.266` / versionCode `906`
- compile / target SDK 37、min SDK 36、libxposed API 102

Application.attachの後に公開ContentProviderから配色設定だけを読みます。Activity生成前に、対象APK内のAppCompatダークオーバーレイとAndroidのダークオーバーレイを重ねます。コンテンツ、入力、クリックリスナー、レイアウト寸法は変更しません。

NativeAppearanceはレイアウト更新時と文字色設定時に、背景と文字のニュートラルカラーを役割別に変換します。色付きインジケーターは維持し、画像ピクセルを変換しません。ColorStateListは元の状態指定を保持し、変換キャッシュに上限を設けます。画像はXMLの単色ベクターUIアイコンのみ対象です。WebViewではAndroidのalgorithmic darkeningを有効にします。タイマーによる走査・画像解析・継続キャプチャはありません。

プロセス内の設定は起動時に固定します。設定変更のためにActivityを再生成しないので、受信済みNFC Intentなどを再実行しません。元アプリへの機器操作APIのフックはありません。

## 保護

未対応バージョンには表示変更を適用しません。初期化に失敗した場合も元のアプリの起動を優先します。連続した描画エラーで表示調整を停止します。過去のApplicationExitInfoに確認できるクラッシュだけを数え、通常の終了・強制停止・プロセス回収を数えません。2分内の3回で停止し、設定画面から解除できます。状態書き込みは自アプリとセサミのUIDだけに許可します。

## ビルドと確認

JDK 21とAndroid SDK 37を使用します。

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:writeRuntimeDependencyList
python3 tools/check_license_catalog.py
```

JVM試験は配色の安定性・透明度・意味のある色の保持、テーマ選択とクラッシュ計数を検証します。実機で一覧・各タブ・ダイアログ・OFF時の復元を確認します。機器の解錠、施錠、照明操作などの物理操作は表示検証では実行しません。

端末画像・ログ・抽出したAPK・逆コンパイル結果はリポジトリにもソースZIPにも含めません。
