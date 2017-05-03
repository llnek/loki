;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.test.loki.test

  (:require [czlab.loki.xpis :as loki :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.loki.session]
        [czlab.loki.player]
        [czlab.loki.util]
        [czlab.basal.meta]
        [czlab.loki.game.core]
        [czlab.loki.game.room]
        [czlab.loki.game.reqs]
        [czlab.loki.net.core]
        [czlab.wabbit.xpis]
        [czlab.basal.format]
        [czlab.basal.meta]
        [czlab.basal.core]
        [czlab.basal.str]
        [clojure.test])

  (:import [czlab.jasal
            Initable
            Startable
            Disposable
            LifeCycle
            Idable
            Restartable
            Hierarchical]
           [czlab.basal Cljrt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  evt-json
  "{\"type\" : 2, \"status\": 200, \"code\":911, \"body\": { \"a\" : 911 }}")
(def ^:private evt-body {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockSocket "" []
  (new<> "io.netty.channel.embedded.EmbeddedChannel"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockDelegate "" []
  (reify
    GameImpl
    (get-player-gist[_ _])
    (on-game-event [_ _])
    (start-round [_ _])
    (end-round [_])
    Restartable
    (restart [_ _])
    (restart [_])
    Initable
    (init [_ _])
    Startable
    (start [_ _])
    (start [_])
    (stop [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockExec "" []
  (let [rts (Cljrt/newrt)]
    (reify
      Execvisor
      (has-child? [_ id] )
      (get-child [_ id] )
      (uptime-in-millis [_] )
      (get-locale [_] )
      (get-start-time [_] )
      (kill9! [_] )
      (cljrt [_] rts)
      (get-scheduler [_] )
      (get-home-dir [_] )

      KeyAccess
      (pkey-bytes [this] (bytesit "hello world"))
      (pkey-chars [_] (.toCharArray "hello world"))

      Idable
      (id [_] "1")

      Startable
      (start [this _] )
      (start [this] )
      (stop [this] )

      SqlAccess
      (acquire-db-pool [_ gid] nil)
      (acquire-db-api [_ gid] nil)
      (dft-db-pool [_] nil)
      (dft-db-api [_] nil)

      Disposable
      (dispose [this]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockPluglet "" []
  (let [exe (mockExec)]
    (reify
      Hierarchical
      (parent [_] exe)
      Idable
      (id [_] "?")
      LifeCycle
      (init [_ _] )
      (start [_] )
      (start [_ _])
      (dispose [_] )
      (stop [_]))))

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
    :network {
      :enabled? true
      :minp 2
      :maxp 2
      :impl  :czlab.test.loki.test/testArena}
    :image "ui/catalog.png"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testArena "" [_ _ ] (mockDelegate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (require 'czlab.nettio.core)
  (is (do->true
        (initGameRegistry! games-meta)))

  (is (let [evt (eventObj<> ::loki/public ::loki/quit evt-body {:x 7})]
        (and (= 2 (get loki-msg-types (:type evt)))
             (= ::loki/ok (:status evt))
             (= 911  (get loki-msg-codes (:code evt)))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (errorObj<> ::loki/public ::loki/quit evt-body {:x 7})]
        (and (= 2 (get loki-msg-types (:type evt)))
             (= ::loki/error (:status evt))
             (= 911 (get loki-msg-codes (:code evt)))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (-> (eventObj<> ::loki/public ::loki/quit evt-body)
                    (encodeEvent ))
            s? (string? evt)]
        (and s?
             (> (.indexOf evt "2") 0)
             (> (.indexOf evt "200") 0)
             (> (.indexOf evt "911") 0))))

  (is (let [evt (-> (eventObj<> ::loki/public ::loki/quit)
                    (encodeEvent))
            s? (string? evt)]
        (and s?
             (> (.indexOf evt "2") 0)
             (> (.indexOf evt "200") 0)
             (> (.indexOf evt "911") 0))))

  (is (let [evt (decodeEvent evt-json {:x 3})]
        (and (= ::loki/public (:type evt))
             (= ::loki/quit (:code evt))
             (== 911 (get-in evt [:body :a])))))

  (is (some? (lookupGame "game-1")))

  (is (let [c1 (lookupPlayer "u1" "p1") ;; user#1
            c2 (lookupPlayer "u1")
            c3 (removePlayer "u1")
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (identical? c1 c2)
             (some? c3)
             (nil? c4))))

  (is (let [c1 (lookupPlayer "u1" "p1") ;; user#2
            c1 (assoc c1 :email "e" :name "n")
            e (:email c1)
            n (:name c1)
            nn (:userid c1)
            id (id?? c1)
            cs (countSessions c1)
            _ (logout c1)
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (= e "e")
             (= n "n")
             (== 0 cs)
             (= nn "u1")
             (not= nn id)
             (nil? c4))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :socket (mockSocket)
                          :body {:gameid gid
                                 :settings {:a 1}
                                 :principal  "u1"
                                 :credential "p1"}}) ;user#3
            t (doPlayReq {:source (mockPluglet)
                          :socket (mockSocket)
                          :body {:gameid gid
                                 :settings {:b 2}
                                 :principal  "u2" ;user#4
                                 :credential "p2"}})
            gid (keyword gid)
            r1 (:roomid (some-> s deref))
            r2 (:roomid (some-> t deref))
            ok
            (and (some? r1)
                 (some? r2)
                 (= r1 r2)
                 (= 1 (:a @s))
                 (= 2 (:b @t))
                 (== 1 (countGameRooms gid))
                 (== 0 (countFreeRooms gid))
                 (not (can-open-room? (lookupGameRoom gid r1))))
            _ (clearFreeRooms gid)
            _ (clearGameRooms gid)]
        (and ok
             (== 0 (countGameRooms gid))
             (== 0 (countFreeRooms gid)))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :socket (mockSocket)
                          :body {:gameid gid
                                 :principal  "u3"
                                 :credential "p3"}}) ;user#5
            gid (keyword gid)
            r (lookupFreeRoom gid
                              (:roomid (some-> s deref)))
            ok
            (and (some? r)
                 (== 1 (countFreeRooms gid))
                 (not (can-open-room? r)))
            _ (removeFreeRoom gid (id?? r))]
        (and ok
             (== 0 (countFreeRooms gid)))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :socket (mockSocket)
                          :body {:gameid gid
                                 :principal  "u4"
                                 :credential "p4"}})
            pu4_ok (lookupPlayer "u4")
            pu4 (:player @s)
            cnt (countSessions pu4)
            r (lookupFreeRoom (keyword gid)
                              (:roomid (some-> s deref)))
            na (not (can-open-room? r))
            t (doJoinReq {:source (mockPluglet)
                          :socket (mockSocket)
                          :body {:roomid (sname (some-> r id??))
                                 :gameid gid
                                 :principal  "u5"
                                 :credential "p5"}})
            gid (keyword gid)
            r2 (lookupGameRoom gid
                               (:roomid (some-> t deref)))
            _ (logout pu4)
            cnt2 (countSessions pu4)
            pu4_nok (lookupPlayer "u4")]
        (and (some? r)
             (some? r2)
             (= 1 cnt)
             (= 0 cnt2)
             (some? pu4_ok)
             (nil? pu4_nok)
             na
             (identical? r r2)
             (not (can-open-room? r2))
             (== 1 (countGameRooms gid))
             (== 0 (countFreeRooms gid))
             (do->true (clearGameRooms gid))
             (do->true (clearFreeRooms gid)))))

  (is (== 90 (rad->deg (deg->rad 90))))

  (is (string? "That's all folks!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

