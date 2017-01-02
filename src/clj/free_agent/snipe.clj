(ns free-agent.snipe
  (:require [free-agent.level :as l])
  (:import [sim.util Proxiable])
  (:gen-class                 ; so it can be aot-compiled
     :name free-agent.snipe)) ; without :name other aot classes won't find it

;; PROOF OF CONCEPT FOR INSPECTOR PROXIES
;; NOT EFFICIENT, NOT COHERENT, AND NOT NECESSARY
;; REAL NEED IS FOR A FUNCTIONAL IMPLEMENTATION OF SNIPES.
;; DOESN'T WORK IN THE END, I THINK BECAUSE POPENV METHODS
;; USE ASSOC OR UPDATE AND END UP MAKING SNIPES INTO MAPS,
;; WHICH THEN COME BACK HERE AND DON'T WORK.

(declare next-id get-curr-snipe record-curr-snipe!  maybe-clear-snipe!  get-field!  make-k-snipe make-r-snipe is-k-snipe?  is-r-snipe?)

(defprotocol SnipeP
  (get-id [this])
  (get-energy [this])
  (get-x [this])
  (get-y [this])
  (set-energy! [this new-val])
  (set-x! [this new-val])
  (set-y! [this new-val]))

;; Does gensym avoid the bottleneck??
(defn next-id 
  "Returns a unique integer for use as an id."
  [] 
  (Long. (str (gensym ""))))

(defn get-curr-snipe
  [id cfg-data$]
  (let [snipe-field (:snipe-field (:popenv @cfg-data$))]
    (some #(= id (get-id %)) (.elements snipe-field))))

(defn record-curr-snipe!
  [id cfg-data$ snipe$]
  (swap! snipe$ assoc :snipe (get-curr-snipe id cfg-data$)))

(def num-snipe-now-accs 3)

(defn maybe-clear-snipe!
  [meths-called$ snipe$]
  (when (= @meths-called$ num-snipe-now-accs)
    (reset! snipe$ nil)
    (reset! meths-called$ 0)))

(defn get-snipe!
  [id cfg-data$ snipe$ meths-called$]
  (when-not @snipe$ (record-curr-snipe! id cfg-data$ snipe$)) ; if snipe$ empty, go get current snipe
  (swap! meths-called$ inc)) ; count how many methods called


(defprotocol InspectedSnipeP
  (getEnergy [this])
  (getX [this])
  (getY [this]))

;; An inspector proxy that will go out and get the current snipe for a given id and return its data
(defrecord SnipeNow [serialVersionUID id cfg-data$ snipe$ meths-called$] ; first arg required by Mason for serialization
  InspectedSnipeP
  (getEnergy [this]
    (get-snipe! id cfg-data$ snipe$ meths-called$)
    (get-energy ^SnipeP @snipe$))
  (getX [this]
    (get-snipe! id cfg-data$ snipe$ meths-called$)
    (get-x ^SnipeP @snipe$))
  (getY [this]
    (get-snipe! id cfg-data$ snipe$ meths-called$)
    (get-y ^SnipeP @snipe$))
  Object
  (toString [this] (str "<SnipeNow #" id ">")))

;; Note levels is a sequence of free-agent.Levels
;; The fields are apparently automatically visible to the MASON inspector system. (!)
(deftype KSnipe [id levels ^:unsynchronized-mutable energy ^:unsynchronized-mutable x ^:unsynchronized-mutable y cfg-data$]
  Proxiable ; for inspectors
  (propertiesProxy [this] (SnipeNow. 1 id cfg-data$ (atom nil) (atom 0)))
  SnipeP
  (get-id [this] id)
  (get-energy [this] energy)
  (get-x [this] x)
  (get-y [this] y)
  (set-energy! [this new-val] (set! energy new-val))
  (set-x! [this new-val] (set! x new-val))
  (set-y! [this new-val] (set! y new-val))
  Object
  (toString [this] (str "<KSnipe #" id " energy: " energy ">")))

(deftype RSnipe [id levels ^:unsynchronized-mutable energy ^:unsynchronized-mutable x ^:unsynchronized-mutable y cfg-data$]
  Proxiable ; for inspectors
  (propertiesProxy [this] (SnipeNow. 1 id cfg-data$ (atom nil) (atom 0)))
  SnipeP
  (get-id [this] id)
  (get-energy [this] energy)
  (get-x [this] x)
  (get-y [this] y)
  (set-energy! [this new-val] (set! energy new-val))
  (set-x! [this new-val] (set! x new-val))
  (set-y! [this new-val] (set! y new-val))
  Object
  (toString [this] (str "<RSnipe #" id " energy: " energy ">")))

(defn make-k-snipe 
  ([cfg-data$ x y]
   (let [{:keys [initial-energy k-snipe-prior]} @cfg-data$]
     (make-k-snipe initial-energy k-snipe-prior x y cfg-data$)))
  ([energy prior x y cfg-data$]
   (KSnipe. (next-id)
            nil ;; TODO construct levels function here using prior
            energy
            x y
            cfg-data$)))

(defn make-r-snipe
  ([cfg-data$ x y]
   (let [{:keys [initial-energy r-snipe-low-prior r-snipe-high-prior]} @cfg-data$]
     (make-r-snipe initial-energy r-snipe-low-prior r-snipe-high-prior x y cfg-data$)))
  ([energy low-prior high-prior x y cfg-data$]
   (RSnipe. (next-id)
            nil ;; TODO construct levels function here using prior (one of two values, randomly)
            energy
            x y
            cfg-data$)))

;; note underscores
(defn is-k-snipe? [s] (instance? free_agent.snipe.KSnipe s))
(defn is-r-snipe? [s] (instance? free_agent.snipe.RSnipe s))


;; Incredibly, the following is not needed in order for snipes to be inspectable.
;; MASON simply sees the record fields as properties.
;; Thank you Clojure and MASON.
;;
;;     (defprotocol InspectedSnipe (getEnergy [this]))
;;     (definterface InspectedSnipe (^double getEnergy []))
;;     To see that this method is visible for snipes, try this:
;;     (pprint (.getDeclaredMethods (class k)))

