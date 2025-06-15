(ns file-checker.core
  (:import (System Console) ; Console をインポート
           (System.IO Directory Path File)
           (System.Text Encoding))
  (:gen-class))

;; ファイル名を検証し、問題点のリスト（文字列のベクター）を返す
(defn validate-filename
  "Validates a single filename against various filesystem rules.
  Returns a vector of error strings if any issues are found, otherwise returns an empty vector."
  [filename]
  (let [basename (try (Path/GetFileNameWithoutExtension filename) (catch Exception _ filename))
        byte-length (.GetByteCount (Encoding/UTF8) filename)]
    (-> []
        ;; 1. ファイル名の長さチェック (255バイト)
        (cond-> (> byte-length 255)
          (conj (str "ファイル名が長すぎます (" byte-length " バイト)。上限は通常255バイトです。")))

        ;; 2. WindowsとNASの禁則文字チェック: \ / : * ? " < > |
        (#(let [matches (distinct (re-seq #"[\\/:*?\"<>|]" filename))]
            (if (seq matches)
              (into % (map (fn [m] (str "禁則文字 '" m "' が含まれています。")) matches))
              %)))

        ;; 3. Windowsの予約名チェック (例: CON, PRN, AUX, NUL, COM1, LPT1)
        (cond-> (re-matches #"(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$" basename)
          (conj (str "Windowsの予約名 '" basename "' が使われています。")))

        ;; 4. macOSでの互換性問題 (: は / に変換される)
        (cond-> (.Contains filename (str \:))
          (conj "文字 ':' はmacOSとの互換性問題を起こす可能性があります。"))

        ;; 5. 先頭の . (隠しファイル)
        (cond-> (.StartsWith filename (str \.))
          (conj "先頭の '.' はUnix系OSで隠しファイルとして扱われます。")))))

;; ディレクトリをスキャンし、問題点のリスト（マップのベクター）を返す
(defn scan-directory
  "Scans a directory and returns a map containing results or an error."
  [target-path]
  (if-not (and target-path (Directory/Exists target-path))
    {:error (str "指定されたフォルダが見つかりません: " target-path)}
    (try
      (let [files (Directory/EnumerateFiles target-path "*" System.IO.SearchOption/AllDirectories)
            results (->> files
                         (map (fn [full-path]
                                (let [filename (Path/GetFileName full-path)
                                      errors (validate-filename filename)]
                                  (when (seq errors)
                                    {:path full-path :errors errors}))))
                         (filter some?))]
        (if (empty? results)
          {:message "問題のあるファイルは見つかりませんでした。"}
          {:results results}))
      (catch System.UnauthorizedAccessException _
        {:error (str "アクセス権エラー: " target-path " のスキャンをスキップしました。")})
      (catch Exception e
        {:error (str "予期せぬエラーが発生しました: " (.Message e))}))))

;; メインの処理
(defn -main
  "Main function to scan a directory."
  [& args]
  ;; --- 文字化け対策：ここから ---
  ;; コンソールの出力文字コードを、システムのデフォルト(日本語環境ならShift_JIS)に設定する
  (set! System.Console/OutputEncoding (System.Text.Encoding/Default))
  ;; --- 文字化け対策：ここまで ---

  (let [target-path (first args)]
    (if-not (and target-path (Directory/Exists target-path))
      (println (str "エラー: 指定されたフォルダが見つかりません。\n"
                    "使い方: Clojure.Main.exe -i check_filenames.clj -m file-checker.core \"C:\\path\\to\\folder\""))
      (do
        (println (str "フォルダをスキャン中: " target-path "\n"))
        (let [scan-result (scan-directory target-path)]
          (cond
            (:error scan-result)
            (println (str "エラー: " (:error scan-result)))

            (:message scan-result)
            (println (:message scan-result))

            (:results scan-result)
            (let [results-data (:results scan-result)]
              (doseq [result results-data]
                (println (str "[警告] " (:path result)))
                (doseq [error (:errors result)]
                  (println (str "  - " error)))
                (println))
              (println (str "\nスキャン完了。 " (count results-data) " 件の問題を検出しました。")))))))))
