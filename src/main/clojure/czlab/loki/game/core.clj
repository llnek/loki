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

  czlab.loki.game.core

  (:require [clojure.java.io :as io]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c])

  (:import [java.io Closeable File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:dynamic *game-rego* {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> GameInfo)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro game<>

  ""
  [seed]

  `(czlab.basal.core/object<> czlab.loki.game.core.GameInfo ~seed))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- loadGames

  ""
  [_ games]

  (c/preduce<map>
    #(let [[k g] %2
           gameid (keyword k)
           {:keys [enabled? minp maxp impl]
            :or {minp 1 maxp 1}}
           (:network g)
           ok (c/!false? enabled?)
           m
           (game<>
             {:maxPlayers (if (c/spos? maxp) maxp minp)
              :minPlayers (if (c/spos? minp) minp 1)
              :supportNetwork ok
              :name (:name g)
              :implClass (and ok
                              (keyword? impl)
                              (u/var?? (u/cljrt<>) impl))
              :id gameid})]
       (assoc! %1 gameid m)) games))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lookup-game

  "Find game from registry"
  [gameid]
  {:pre [(some? gameid)]}

  (when-some [g (*game-rego* (keyword gameid))]
    (c/debug "found game with id = %s" gameid)
    g))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn init-game-registry!

  "Initialize the game registry"
  [games]

  {:pre [(map? games)]}

  (alter-var-root #'czlab.loki.game.core/*game-rego* loadGames games)
  (c/info "games=\n%s" *game-rego*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

