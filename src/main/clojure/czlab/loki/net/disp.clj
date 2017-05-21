;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.disp

  (:require [czlab.loki.xpis :as loki]
            [czlab.basal.log :as log]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.str :as s]
            [czlab.loki.net.core :as nc])

  (:import [czlab.jasal Sendable Receivable]
           [java.io Closeable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-object EventSubr
  Receivable
  (receive [me evt]
    (when (= (:type me)
             (:type evt))
      (log/debug "[%s]: recv'ed msg: %s" (c/id?? me) (nc/prettyEvent evt))
      (.send ^Sendable
             (:session me) evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro esubr<> "" [session]
  `(c/object<> czlab.loki.net.disp.EventSubr
               {:id (czlab.basal.str/toKW
                      "subr#" (czlab.basal.core/seqint2))
                :session ~session
                :type ::loki/public }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-atomic EventDispatcher
  loki/PubSub
  (unsubsc-if-session [me s]
    (doseq [[su _] (:handlers @me)
            :let [s (:session su)]
            :when (c/objEQ? su s)]
      (.unsubsc me su)))
  (pub-event [me msg]
    (log/debug "pub msg = %s" (:code msg))
    (doseq [[_ c] (:handlers @me)]
      (cas/go (cas/>! c msg))))
  (unsubsc [me su]
    (when-some [c (get (:handlers @me) su)]
      (cas/close! c)
      (c/alter-atomic me
                      update-in
                      [:handlers] dissoc su)))
  (subsc [me su]
    (let [^Receivable r su
          c (cas/chan 4)]
      (c/alter-atomic me
                      update-in [:handlers] assoc su c)
      (cas/go-loop []
        (when-some [msg (cas/<! c)]
          (if (= (:type su)
                 (:type msg))
            ;;cant type hint inside async code
            (.receive r msg))
          (recur)))))
  Closeable
  (close [me]
    (doseq [[_ c] (:handlers @me)]
      (cas/close! c))
    (c/alter-atomic me
                    assoc :handlers {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro edispatcher<> "" []
  `(czlab.basal.core/atomic<>
     czlab.loki.net.disp.EventDispatcher
     {:id (czlab.basal.str/toKW
            "disp#" (czlab.basal.core/seqint2)) :handlers {}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

