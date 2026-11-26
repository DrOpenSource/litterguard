package app.litterguard.core.ml

import app.litterguard.core.model.ObjectCategory

/** The 80 COCO class names in model output order, and what each means to the dumping logic (FR-2.3). */
internal object CocoClasses {
    val names: List<String> =
        (
            "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,traffic light,fire hydrant,stop sign," +
                "parking meter,bench,bird,cat,dog,horse,sheep,cow,elephant,bear,zebra,giraffe,backpack,umbrella," +
                "handbag,tie,suitcase,frisbee,skis,snowboard,sports ball,kite,baseball bat,baseball glove," +
                "skateboard,surfboard,tennis racket,bottle,wine glass,cup,fork,knife,spoon,bowl,banana,apple," +
                "sandwich,orange,broccoli,carrot,hot dog,pizza,donut,cake,chair,couch,potted plant,bed," +
                "dining table,toilet,tv,laptop,mouse,remote,keyboard,cell phone,microwave,oven,toaster,sink," +
                "refrigerator,book,clock,vase,scissors,teddy bear,hair drier,toothbrush"
        ).split(",")

    private val vehicles = setOf("bicycle", "car", "motorcycle", "bus", "truck")
    private val animals =
        setOf("bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe")

    fun category(label: String): ObjectCategory =
        when (label) {
            "person" -> ObjectCategory.PERSON
            in vehicles -> ObjectCategory.VEHICLE
            in animals -> ObjectCategory.ANIMAL
            else -> ObjectCategory.OTHER
        }
}
