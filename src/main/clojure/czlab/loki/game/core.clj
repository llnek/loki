;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.core

  (:require [czlab.basal.log :as log]
            [clojure.java.io :as io]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s])

  (:import [czlab.jasal Idable]
           [czlab.basal Cljrt]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *game-rego* {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-object GameInfo)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro game<>
  "" [seed]
  `(czlab.basal.core/object<> czlab.loki.game.core.GameInfo ~seed))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- loadGames "" [_ games]
  (c/preduce<map>
    #(let [[k g] %2
           gameid (keyword k)
           {:keys [enabled? minp maxp impl]
            :or {minp 1 maxp 1 impl ""}}
           (:network g)
           ok (c/!false? enabled?)
           impl (s/strKW impl)
           m
           (game<>
             {:maxPlayers (if (c/spos? maxp) maxp minp)
              :minPlayers (if (c/spos? minp) minp 1)
              :supportNetwork ok
              :name (:name g)
              :implClass (with-open
                           [clj (Cljrt/newrt)]
                           (if ok (.varIt clj impl)))
              :id gameid})]
       (assoc! %1 gameid m)) games))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn initGameRegistry!
  "Initialize the game registry"
  [games]
  {:pre [(map? games)]}

  (alter-var-root #'czlab.loki.game.core/*game-rego* loadGames games)
  (log/info "games=\n%s" *game-rego*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGame
  "Find game from registry"
  [gameid]
  {:pre [(some? gameid)]}

  (when-some [g (*game-rego* (keyword gameid))]
    (log/debug "found game with id = %s" gameid)
    g))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

