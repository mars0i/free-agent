(ns free-agent.popenv
  (:require [free-agent.snipe :as sn]
            [free-agent.mush :as mu]
            [utils.random :as ran])
  (:import [sim.field.grid Grid2D ObjectGrid2D]
           [sim.util IntBag]))

;(use '[clojure.pprint]) ; DEBUG

(defrecord PopEnv [snipe-field mush-field])

(defn make-popenv
  [rng cfg-data]
  (let [{:keys [env-width env-height]} cfg-data
        snipe-field (ObjectGrid2D. env-width env-height)
        mush-field  (ObjectGrid2D. env-width env-height)]
    (PopEnv. snipe-field mush-field)))

(defn add-snipe
  "Create and add a snipe to field using snipe-maker, which expects x and y
  coordinates as arguments.  May be inefficient if there are the number of
  snipes is a large proportion of the number of cells in the snipe field."
  [rng field width height snipe-maker]
  (loop []
    (let [x (ran/rand-idx rng width)
          y (ran/rand-idx rng height)]
      (if-not (.get field x y) ; don't clobber another snipe; empty slots contain Java nulls, i.e. Clojure nils
        (.set field x y (snipe-maker x y))
        (recur)))))

(defn add-k-snipes
  [rng cfg-data field]
  (let [{:keys [env-width env-height initial-energy k-snipe-prior num-k-snipes]} cfg-data]
    (dotimes [_ (:num-k-snipes cfg-data)] ; don't use lazy method--it may never be executed
      (add-snipe rng field env-width env-height 
                 (fn [x y] (sn/make-k-snipe initial-energy k-snipe-prior x y))))))

(defn add-r-snipes
  [rng cfg-data field]
  (let [{:keys [env-width env-height initial-energy r-snipe-low-prior r-snipe-high-prior num-r-snipes]} cfg-data]
    (dotimes [_ num-r-snipes]
      (add-snipe rng field env-width env-height 
                 (fn [x y] (sn/make-r-snipe initial-energy r-snipe-low-prior r-snipe-high-prior x y))))))

(defn maybe-add-mush!
  [rng cfg-data field x y]
  (when (< (ran/next-double rng) (:mush-prob cfg-data)) ; flip biased coin to decide whether to grow a mushroom
    (let [{:keys [env-center mush-low-mean mush-high-mean 
                  mush-sd mush-pos-nutrition mush-neg-nutrition]} cfg-data
          [low-mean-nutrition high-mean-nutrition] (if (< x env-center) ; subenv determines whether low vs high reflectance
                                                     [mush-pos-nutrition mush-neg-nutrition]   ; paired with
                                                     [mush-neg-nutrition mush-pos-nutrition])] ; nutritious vs poison
      (.set field x y 
            (if (< (ran/next-double rng) 0.5) ; half the mushrooms are of each kind in each subenv, on average
              (mu/make-mush mush-low-mean  mush-sd low-mean-nutrition)
              (mu/make-mush mush-high-mean mush-sd high-mean-nutrition))))))

;; TODO Do I really need so many mushrooms?  They don't change.  Couldn't I just define four mushrooms,
;; and reuse them?  (If so, be careful about their deaths.)
(defn add-mushs
  "For each patch in mush-field, optionally add a new mushroom, with 
  probability (:mush-prob cfg-data)."
  [rng cfg-data field]
  (let [{:keys [env-width env-height mush-prob
                mush-low-mean mush-high-mean mush-sd 
                mush-pos-nutrition mush-neg-nutrition]} cfg-data]
    (doseq [x (range env-width)
            y (range env-height)]
      (maybe-add-mush! rng cfg-data field x y))))

(defn populate
  [rng cfg-data popenv]
  (let [{:keys [env-width env-height]} cfg-data
        mush-field (:mush-field popenv)
        snipe-field    (:snipe-field popenv)]
    (.clear mush-field)
    (add-mushs rng cfg-data mush-field)
    (.clear snipe-field)
    (add-k-snipes rng cfg-data snipe-field)
    (add-r-snipes rng cfg-data snipe-field)
    (PopEnv. snipe-field mush-field)))

;; reusable bags
(def x-coord-bag (IntBag. 6))
(def y-coord-bag (IntBag. 6))

;; TODO also need to update mush-field with new mushrooms. or just reuse the old ones, since they have no state.

(defn perceive-mushroom [snipe mush] [snipe true]) ; FIXME

(defn if-appetizing-then-eat 
  [snipe mush] 
  (let [[experienced-snipe appetizing?] (perceive-mushroom snipe mush)]
    (if appetizing?
      [(update experienced-snipe :energy + (:nutrition mush)) true]
      [experienced-snipe false])))

;; FIXME:
(defn snipes-eat-old
  [rng cfg-data snipes snipe-field mush-field]
  [snipe-field mush-field]) ; FIXME

;; new version
(defn snipes-eat
  [rng cfg-data snipes snipe-field mush-field]
  [snipe-field mush-field]
  (let [snipes-plus-eaten? (for [snipe snipes    ; returns only snipes on mushrooms
                                 :let [{:keys [x y]} snipe
                                       mush (.get mush-field x y)]
                                 :when mush]
                             (if-appetizing-then-eat snipe mush))]
    (doseq [[snipe eaten?] snipes-plus-eaten?] ;; TODO For now do it destructively (no need to clear, and leave be snipes w/out mushrooms)
      (let [{:keys [x y]} snipe]
        (.set snipe-field x y snipe) ; replace old snipe with new, more experienced snipe, or maybe the same one
        (.set mush-field x y nil)
        (maybe-add-mush! rng cfg-data mush-field x y)))) ; FIXME this probably isn't really what I want here
  [snipe-field mush-field]) ; TODO currently returning same fields, but with modifications

(defn choose-next-loc
  "Return a pair of field coordinates randomly selected from the empty 
  hexagonally neighboring locations of snipe's location, or the current
  location if all neighboring locations are filled."
  [rng snipe-field snipe]
  (let [curr-x (:x snipe)
        curr-y (:y snipe)]
    (.getHexagonalLocations snipe-field              ; inserts coords of neighbors into x-pos and y-pos args
                            curr-x curr-y
                            1 Grid2D/TOROIDAL false  ; immediate neighbors, toroidally, don't include me
                            x-coord-bag y-coord-bag) ; will hold coords of neighbors
    (let [candidate-locs (for [[x y] (map vector (.toIntegerArray x-coord-bag)  ; x-pos, y-pos have to be IntBags
                                          (.toIntegerArray y-coord-bag)) ; but these are not ISeqs like Java arrays
                               :when (not (.get snipe-field x y))] ; when cell is empty
                           [x y])]
      (if (seq candidate-locs) ; when not empty
        (let [len (count candidate-locs)
              idx (ran/rand-idx rng len)
              [next-x next-y] (nth candidate-locs idx)]
          {[next-x next-y] [snipe]}) ; key is a pair of coords; val is a single-element vector containing a snipe
        {[curr-x curr-y] [snipe]}))))

(defn move-snipe
  [snipe-field x y snipe]
  (.set snipe-field x y (assoc snipe :x x :y y)))

(defn move-snipes
  [rng cfg-data snipes snipe-field]
  (let [loc-snipe-vec-maps (for [snipe snipes  ; make seq of maps with a coord pair as key and singleton seq containing snipe as val
                                 :let [next-loc (choose-next-loc rng snipe-field snipe)]] ; can be current loc
                             next-loc)
        loc-snipe-vec-map (apply merge-with concat loc-snipe-vec-maps) ; convert sec of maps to a single map where snipe-vecs with same loc are concatenated
        loc-snipe-map (into {}                                       ; collect several maps into one
                            (for [[coords snipes] loc-snipe-vec-map] ; go through key-value pairs, where values are collections containing one or more snipes
                              (let [len (count snipes)]              ; convert to key-value pairs where value is a snipe
                                (if (= len 1)
                                  [coords (first snipes)]                          ; when more than one
                                  (let [mover (nth snipes (ran/rand-idx rng len))] ; randomly select one to move
                                    (into {coords mover} (map (fn [snipe] {[(:x snipe) (:y snipe)] snipe}) ; and make others "move" to current loc
                                                              (remove #(= mover %) snipes))))))))]         ; (could be more efficient to leave them alone)
    ;; TODO (?) For now, update snipe-field Mason-style, i.e. modifying the same field every time:
    (.clear snipe-field)
    (doseq [[[x y] snipe] loc-snipe-map] ; will convert the map into a sequence of mapentries, which are seqs
      (move-snipe snipe-field x y snipe))
    ;; Since we destructively modified snipe-field, we don't have to assoc it in to a new popenv (oh...)
    snipe-field))

(defn next-popenv
  [popenv rng cfg-data] ; put popenv first so we can swap! it
  (let [{:keys [snipe-field mush-field]} popenv
        snipes (.elements snipe-field)
        snipe-field (move-snipes rng cfg-data snipes snipe-field)
        [snipe-field mush-field] (snipes-eat rng cfg-data snipes snipe-field mush-field)]
    (PopEnv. snipe-field mush-field)))
