;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.test.loki.core

  (:require [czlab.loki.game.core :as gc]
            [czlab.loki.game.room :as gr]
            [czlab.loki.game.reqs :as rs]
            [czlab.bixby.exec :as be]
            [czlab.bixby.core :as bc]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [clojure.test :as ct]
            [czlab.loki.player :as p]
            [czlab.loki.util :as u]
            [czlab.basal.meta :as m]
            [czlab.basal.util :as bu]
            [czlab.basal.core :as c
             :refer [ensure?? ensure-thrown??]]
            [czlab.basal.io :as i]
            [czlab.loki.xpis :as loki]
            [czlab.loki.session :as ss]
            [czlab.test.loki.g1 :as g1]
            [czlab.loki.net.core :as nc])

  ;(:import [czlab.jasal Initable Startable Disposable LifeCycle Idable Restartable Hierarchical] [czlab.basal Cljrt])
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;(def ^:private evt-json (i/fmt->json {:type 2 :status 200 :code 911 :body {:a 911}}))
;(def ^:private evt-body {:a 911})

(def
  ^:private
  evt-json
  "{\"type\" : 2, \"status\": 200, \"code\":911, \"body\": { \"a\" : 911 }}")
(def ^:private evt-body {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  games-meta
  {:game-1
   {:layout "portrait",
    :name  "Test",
    :description "Fun!",
    :keywords "",
    :height  480,
    :width  320
    :pubdate #inst "2016-01-01"
    :author "joe"
    :network {:enabled? true
              :minp 2
              :maxp 2
              :impl :czlab.test.loki.g1/g1Impl }
    :image "ui/catalog.png"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/deftest test-core

  (ensure?? "init-game-reg" (c/do->true
                              (gc/init-game-registry! games-meta)))

  (ensure?? "event structure (1)" (let [evt (nc/eventObj<> loki/type-public loki/quit evt-body {:x 7})]
                                     (and (= "public" (loki/msg-types (:type evt)))
                                          (= loki/status-ok (:status evt))
                                          (= "quit" (loki/msg-codes (:code evt)))
                                          (map? (:body evt)) (== 7 (:x evt)))))

  (ensure?? "event structure (2)" (let [evt (nc/errorObj<> loki/type-public loki/quit evt-body {:x 7})]
                                    (and (= "public" (loki/msg-types (:type evt)))
                                         (= loki/status-error (:status evt))
                                         (= "quit" (loki/msg-codes (:code evt)))
                                         (map? (:body evt))
                                         (== 7 (:x evt)))))

  (ensure?? "encode-event (1)" (let [evt (-> (nc/eventObj<> loki/type-public loki/quit evt-body)
                                             nc/encode-event)
                                     s? (string? evt)]
                                 (and s?
                                      (> (.indexOf evt "2") 0)
                                      (> (.indexOf evt "200") 0)
                                      (> (.indexOf evt "911") 0))))

  (ensure?? "encode-event (2)" (let [evt (-> (nc/eventObj<> loki/type-public loki/quit)
                                             nc/encode-event)
                                     s? (string? evt)]
                                 (and s?
                                      (> (.indexOf evt "2") 0)
                                      (> (.indexOf evt "200") 0)
                                      (> (.indexOf evt "911") 0))))

  (ensure?? "decode-event (2)" (let [evt (nc/decode-event evt-json {:x 3})]
                                 (and (= loki/type-public (:type evt))
                                      (= loki/quit (:code evt))
                                      (== 911 (get-in evt [:body :a])))))

  (ensure?? "game exists" (some? (gc/lookup-game "game-1")))

  (ensure?? "player exists" (let [c1 (p/lookup-player "u1" "p1") ;; user#1
                                  c2 (p/lookup-player "u1")
                                  c3 (p/remove-player "u1")
                                  c4 (p/lookup-player "u1")]
                              (and (some? c1)
                                   (identical? c1 c2)
                                   (some? c3)
                                   (nil? c4))))

  (ensure?? "player update" (let [c1 (p/lookup-player "u1" "p1") ;; user#2
                                  c1 (assoc c1 :email "e" :name "n")
                                  e (:email c1)
                                  n (:name c1)
                                  nn (:userid c1)
                                  id (c/id?? c1)
                                  cs (ss/count-sessions c1)
                                  _ (p/logout c1)
                                  c4 (p/lookup-player "u1")]
                              (and (some? c1)
                                   (= e "e")
                                   (= n "n")
                                   (== 0 cs)
                                   (= nn "u1")
                                   (not= nn id)
                                   (nil? c4))))

(comment

  (ensure?? "play-req(1)" (let [cwd (bu/get-user-dir)
                                e (be/start-via-api ["--home" (bu/fpath cwd)])]
                            (Thread/sleep 3000)
                            true))
)

  (ensure?? "play-req (1)" (let [gid "game-1"
                                 s (rs/do-playreq {:source nil
                                                   :socket nil
                                                   :body {:gameid gid
                                                          :settings {:a 1}
                                                          :principal  "u1"
                                                          :credential "p1"}}) ;user#3
                                 t (rs/do-playreq {:source nil
                                                   :socket nil
                                                   :body {:gameid gid
                                                          :settings {:b 2}
                                                          :principal  "u2" ;user#4
                                                          :credential "p2"}})
                                 gid (keyword gid)
                                 r1 (c/getf s :roomid)
                                 r2 (c/getf t :roomid)
                                 ok
                                 (and (some? r1)
                                      (some? r2)
                                      (= r1 r2)
                                      (= 1 (c/getf s :a))
                                      (= 2 (c/getf t :b))
                                      (== 1 (gr/count-game-rooms gid))
                                      (== 0 (gr/count-free-rooms gid))
                                      (not (loki/can-open-room? (gr/lookup-game-room gid r1))))
                                 _ (gr/clear-free-rooms gid)
                                 _ (gr/clear-game-rooms gid)]
                             (and ok
                                  (== 0 (gr/count-game-rooms gid))
                                  (== 0 (gr/count-free-rooms gid)))))

  (ensure?? "play-req(2)" (let [gid :game-1
                                s (rs/do-playreq {:source nil
                                                  :socket nil
                                                  :body {:gameid gid
                                                         :principal  "u3"
                                                         :credential "p3"}}) ;user#5
                                r (gr/lookup-free-room gid
                                                       (c/getf s :roomid))
                                ok
                                (and (some? r)
                                     (== 1 (gr/count-free-rooms gid))
                                     (not (loki/can-open-room? r)))
                                _ (gr/remove-free-room gid (c/id r))]
                            (and ok
                                 (== 0 (gr/count-free-rooms gid)))))

  (ensure?? "join-req(1)" (let [gid :game-1
                                s (rs/do-playreq {:source nil
                                                  :socket nil
                                                  :body {:gameid gid
                                                         :principal  "u4"
                                                         :credential "p4"}})
                                pu4_ok (p/lookup-player "u4")
                                pu4 (c/getf s :player)
                                cnt (ss/count-sessions pu4)
                                r (gr/lookup-free-room gid (c/getf s :roomid))
                                na (not (loki/can-open-room? r))
                                t (rs/do-joinreq {:source nil
                                                  :socket nil
                                                  :body {:roomid (c/id r)
                                                         :gameid gid
                                                         :principal  "u5"
                                                         :credential "p5"}})
                                r2 (gr/lookup-game-room gid (c/getf t :roomid))
                                _ (p/logout pu4)
                                cnt2 (ss/count-sessions pu4)
                                pu4_nok (p/lookup-player "u4")]
                            (and (some? r)
                                 (some? r2)
                                 (= 1 cnt)
                                 (= 0 cnt2)
                                 (some? pu4_ok)
                                 (nil? pu4_nok)
                                 na
                                 (identical? r r2)
                                 (not (loki/can-open-room? r2))
                                 (== 1 (gr/count-game-rooms gid))
                                 (== 0 (gr/count-free-rooms gid))
                                 (c/do->true (gr/clear-game-rooms gid))
                                 (c/do->true (gr/clear-free-rooms gid)))))

  (ensure?? "rad<->deg" (== 90 (u/rad->deg (u/deg->rad 90))))

  (ensure?? "test-end" (== 1 1)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(ct/deftest
  ^:test-core loki-test-core
  (ct/is (c/clj-test?? test-core)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

