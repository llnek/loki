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

  czlab.loki.player

  (:require [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.loki.session :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; player-db
;; {player-id -> {:p player :s {id -> session}}}
(def ^:private player-db (atom {}))
;; map of nicknames
(def ^:private userid-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> LokiPlayer)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- player<>

  ""
  [userid passwd]

  (c/object<> LokiPlayer
              {:userid userid
               :passwd passwd
               :id (c/x->kw "player#" (u/seqint2))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- create-player

  ""
  [^String userid ^chars passwd]

  {:pre [(c/hgl? userid)]}

  (locking userid-db
    (if (c/!in? @userid-db userid)
      (let [p (player<> userid passwd)
            pid (:id p)]
        (swap! player-db assoc pid p)
        (swap! userid-db assoc userid pid))))
  (@player-db (@userid-db userid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn remove-player

  ""
  [userid]

  (locking userid-db
    (when-some [pid (@userid-db userid)]
      (swap! userid-db dissoc userid)
      (swap! player-db dissoc pid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lookup-player

  ""

  ([userid pwd]
   (create-player userid pwd))
  ([userid] (-> (@userid-db userid) (@player-db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn logout

  ""
  [player]

  (ss/remove-sessions player)
  (remove-player (:userid player)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

