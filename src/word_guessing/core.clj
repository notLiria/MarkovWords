(ns word-guessing.core
  (:require [clojure.string :as str]
            [clj-http.client :as client]
            [clj-json.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [clojure.java.jdbc :as sql])
  (:gen-class)
  )



(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def word-set-test [
                    {:words '("Not" "this" "one") :occ 2}
                    {:words '("This" "is" "hard") :occ 52}
                    {:words '("Hello" "World") :occ 1}
                    ])

;;(def stash-api-request ((client/get "http://api.pathofexile.com/public-stash-tabs/?id=2524-4136-3334-4616-1278") :body))

;;(def raw-stashes (second (second (json/parse-string stash-api-request))))



(def db
  {:classname "org.sqlite.JDBC",
   :subprotocol "sqlite",
   :subname "test.db"}
)

(def table
  (sql/create-table-ddl :words
                        [:id :integer :primary :key]
                        [:words :text]
                        [:w1 :text]
                        [:w2 :text]
                        [:w3 :text]
                        [:occ :num])
  )


(defn drop-nth [n coll]
  (keep-indexed #(if (not= %1 n) %2) coll))

(defn take-first-three
  [x]
  (if (> (count x) 3)
    (take 3 x)
    x))


(defn c-filter
  [x y]
  (if (= y (x :words))
    x))

(defn update-if
  [input word]
  (if (= word (input :words))
    (update-in input [:occ] inc)
    input))

(defn contains-words?
  ;; PAss it a sequence of maps to seach through, returns true on the first true result
  [words input]
  (if (< 0 (count input))
    (do
      (if (= words ((first input) :words))
        true
        (recur words (rest input))))
    false))

(defn generate-three-word-sets
;; Input a string
  ([input]
   (generate-three-word-sets (str/split input #" ") 
                             []))
  ([input output]
   (if (< 2 (count input))
     (let [x (take-first-three input)]
       (if (contains-words? x output)
         (recur (rest input)
                (map #(update-if % x) output))
         (recur (rest input)
                (conj output {:words x :occ 1}))))
     output)))

(defn find-sets-that-start-with
  [input parameter]
  (if (= (first (input :words)) parameter)
    input
    nil))

(defn filter-candidates
  ;; Looks for sets of words in arr that begin with parameter and sort
  [arr parameter]
  (let [x (sort-by :occ
                   (filter #(find-sets-that-start-with % parameter) 
                           arr))]
    (if (not-empty x)
      x
      arr)
    ))

(defn gen-sentences
  ;; input is the output of gen-sentences
  ;; Remaining is an integer

  ([input remaining]
   (let [capitals (filter 
                   #(Character/isUpperCase (first (first (% :words))))
                   input)
         i (rand-int (count capitals))]
     (if (not-empty capitals)
       (do
         (let [found-one (nth capitals i)]
           (gen-sentences (remove #(= (% :words) (found-one :words)) 
                                  input)
                          (conj [] (found-one :words))
                          (dec remaining))))
       (println "SET TOO SMALL"))))
  ([input output remaining]
   (if (< 0 remaining)
     (let [candidates (filter-candidates input (last (last output)))]
       (if (not-empty candidates)
         (let [i (rand-int (count candidates))
               found-one (nth candidates i)]
           (recur (remove #(= (% :words) (found-one :words))
                          input)
                  (conj output (rest (found-one :words)))
                  (dec remaining)))
         (str/join " " (map #(str/join " " %) output))))
       (str/join " " (map #(str/join " " %) output)))))


(defn get-dom
  [link]
  (html/html-snippet (:body @ (http/get link {:insecure? true}))))



(defn get-reddit-user-comments
  [user]
  (map #(first (:content %))
   (html/select (get-dom (str "https://www.reddit.com/user/" user))
                 [:div.content
                  :div.comment
                  :form.usertext
                  :div.md 
                  :p])))


(defn get-subreddit-links
  ;; Give it a subreddit and it will generate a thing to 
  ;; append to reddit.com/
  [link]
  (filter #(str/includes? % "/r/")
   (filter #(not= nil %) 
           (map #(:href (:attrs (first (:content %)))) 
                (html/select (get-dom link) 
                             [:div.content 
                              :div.link 
                              :div.entry 
                              :p.title])))))


(defn get-comments-from-reddit-link
  ;; Scrape comments off of a subreddit thread
  [link]
  (map #(first %)
       (map #(:content %) 
            (html/select
             (html/html-snippet 
              (:body @ (http/get link {:insecure? true})))
             [:div.commentarea :div.comment :div.md :p]))))



(defn write-set-to-db
  [set]
  (if (not-empty set)
    (do
      (write-hash-to-db (first set))
      (recur (rest set)))))


(defn write-hash-to-db
;; Write a hash of theform {:words :occ} to the db
  [a-hash]
  
  (if (empty? (sql/query db ["select * from words where words=?"
                             (str/join " " (:words a-hash))]))
    (sql/insert! db :words
                 {:words (str/join " " (:words a-hash))
                  :w1 (first (:words a-hash))
                  :w2 (second (:words a-hash))
                  :w3 (last (:words a-hash))
                  :occ (:occ a-hash)})
    (let [record (first 
                  (sql/query db ["select * from words where words=?"
                                 (str/join " " (:words a-hash))]))]
      (sql/update! db :words 
                   {:occ (inc (:occ record))}
                   ["id=?" (:id record)]))))
    

(defn clear-db 
  []
  (sql/execute! db ["delete from words"]))


(defn analyze-reddit-user-helper
  [comments]
  (if (not-empty comments)
    (do
      (write-set-to-db (generate-three-word-sets (first comments)))
      (recur (rest comments))
      )
    )

)
(defn analyze-reddit-user
  ([user]
   (analyze-reddit-user-helper (get-reddit-user-comments user))
   )
)

