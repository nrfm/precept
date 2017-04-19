(ns libx.util-test
    (:require [clojure.test :refer [deftest testing is run-tests]]
              [libx.tuplerules :refer [def-tuple-session]]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [libx.util :refer :all]
              [clojure.spec :as s]
              [clara.rules :refer [query defquery fire-rules] :as cr]
              [clara.tools.tracing :as trace]))

(defn todo-tx [id title done]
  (merge
    {:db/id      id
     :todo/title title}
    (when-not (nil? done)
      {:todo/done done})))

(defn is-tuple? [x]
  (and (vector x)
    (= (count x) 3)
    (keyword? (second x))))

(deftest map->tuples-test
  (testing "Converting an entity map to a vector of tuples"
    (let [entity-map (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          entity-vec (map->tuples entity-map)]
      (is (map? entity-map)
        "Should receive a hashmap as input")
      (is (vector? entity-vec)
        "Should return a vector")
      (is (every? is-tuple? entity-vec)
        "Every entry in main vector should pass the tuple test")
      (is (every? #(= (first %) (:db/id entity-map)) entity-vec)
        "Every entry should use the :db/id from the map for its eid"))))

(deftest insertable-test
  (let [m-fact  (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
        m-facts (into [] (repeat 5 m-fact))]
    (is (every? is-tuple? (insertable m-fact)))
    (is (every? is-tuple? (insertable m-facts)))
    (is (every? is-tuple? (insertable (insertable m-fact))))
    (is (every? is-tuple? (insertable (insertable m-facts))))))

(deftest insert-test
  (testing "Insert single tuple"
    (let [session @(def-tuple-session mysess)
          fact [-1 :foo "bar"]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= 1 (count (:facts (first trace)))))))
  (testing "Insert tuples"
    (let [session @(def-tuple-session mysess)
          facts [[-1 :foo "bar"]
                 [-1 :bar "baz"]
                 [-2 :foo "baz"]]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert facts)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (count facts) (count (:facts (first trace)))))))
  (testing "Insert single map"
    (let [session @(def-tuple-session mysess)
          fact  (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (dec (count (keys fact))) (count (:facts (first trace)))))))

  (testing "Insert vector of maps"
    (let [session @(def-tuple-session mysess)
          numfacts 5
          m-fact  #(todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          m-facts (into [] (repeatedly numfacts m-fact))
          trace (trace/get-trace (-> session
                                  (trace/with-tracing)
                                  (insert-fire m-facts)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (count (:facts (first trace)))
             (* numfacts (dec (count (keys (m-fact))))))))))

(deftest with-changes-test
  (let [changes-added (with-changes {:db/id 1 :foo "bar"})
        [facts changes] (partition-by #(= :db/change (second %)) changes-added)
        every-change-a-change (every? #(s/valid? :db/change  %) changes)
        change-facts (map last changes)]
     (is every-change-a-change)
     (is (= change-facts facts))))

(run-tests)