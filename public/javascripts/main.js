$(document).ready(function(){
    var last_created_id = null;
    var default_image_path = "/assets/images/default.jpg";

    var delay = (function(){
      var timer = 0;
      return function(callback, ms){
        clearTimeout (timer);
        timer = setTimeout(callback, ms);
      };
    })();

    $("#search").keyup(function(){
        var term = $(this).val().trim().toLowerCase();
        var query = {"search" : {"title" : term, "description" : term}, "size" : 30};
        var label = window.location.pathname.split("/")[1];
        if(label !== ""){
            if(query.filter === undefined) query.filter = {};
            query.filter.labels = label;
        }

        delay(function(){
            $("#trip_show").addClass("hide");
            $(".carousel").carousel("pause");
            $("#trips").css({"opacity" : "0.5"});
            $("#search_spinner").removeClass("hide");
            $.ajax({
                url: "/trips/browse",
                type: "POST",
                processData: false,
                contentType: "application/json",
                data: JSON.stringify(query),
                success: function(trips){
                    var trips_html = "";
                    for(var i = 0; i < trips.length; i++){
                        trips_html += renderTrip(trips[i]);
                    }

                    $("#trips").html(trips_html);
                    $("#trips").css({"opacity" : "1"});
                    $("#search_spinner").addClass("hide");
                }
            })
        }, 350)
    });

    $("form#add_trip").submit(function(){
        $(".carousel").carousel("pause");
        var url = $(this).attr("action");
        var fd = new FormData();
        fd.append("title", $("#title").val());
        fd.append("description", $("#description").val());
        $("#add_trip_action").button("loading");

        var labels = $("#check_labels input:checkbox:checked").map(function(){
            return $(this).attr("value").split("_").map(function(item){
                return item[0].toUpperCase() + item.substring(1, item.length);
            }).join(" ");
        });

        if($("#custom_label").val().trim() !== "") labels.push($("#custom_label").val());

        var labels_arr = [];
        for(var i = 0; i < labels.length; i++){ labels_arr.push(labels[i]) }

//        $("#add_trip_spinner").removeClass("hide");
        $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify({
                    "title" : $("#title").val(),
                    "description" : $("#description").val(),
                    "labels" : labels_arr,
                    "type" : $("#type").val(),
                    "start_date" : $("#start_date").val(),
                    "end_date" : $("#end_date").val()
                }),
                processData: false,
                contentType: "application/json",
                success: function(res){

                    $("#error_message").addClass("hide");
                    $("#image_upload_wrapper").removeClass("hide");
                    last_created_id = res.id;

                    getAndRenderTrips();

                    $("#trip_show").addClass("hide");
                    $("#upload_progress").addClass("hide");
                    $("#add_trip_action").button("reset");
                    $("#add_trip_action").addClass("hide");
                    $("#reset_trip_action").removeClass("hide");
                },
                error: function(jqXHR, textStatus, errorThrown ){
                    $("#error_message").removeClass("hide").html(jqXHR.responseText);
                    $("#image_upload_wrapper").addClass("hide");
                    $("#add_trip_action").button("reset");
                }
        });
        return false;
    });

    $("#reset_trip_action").click(function(){
        $("#add_trip_action").removeClass("hide");
        $(this).addClass("hide");
    });

    var trips_per_row = 0;
    function getAndRenderTrips(){
            $.ajax({
                url: "/trips/browse",
                type: "POST",
                data: JSON.stringify({"sort" : {"updated_timestamp" : "desc"}, "size" : 30}),
                processData: false,
                contentType: "application/json",
                success: function(trips){
                    $("#trips").html("");
                    var index = 0;

                    var trips_html = '';
                    for(var i = 0; i < trips.length; i++){
                        trips_html += renderTrip(trips[i]);
                    }
                    $("#trips").html(trips_html);

                    $("#search_spinner").addClass("hide");
//                    $("#add_trip_spinner").addClass("hide");
                    $("#trips").css({"opacity" : "1"});
                }
            });

    }

    function renderTrip(trip){
        var html = '<div class="col-md-3 trip" id="' + trip.id + '">' +
                        '<div class="thumbnail">' +
                            '<a href="#" class="open-trip"><img src="' + trip.image_collection[0] + '" /></a>' +
                            '<div class="caption">' +
                               '<a href="#" class="open-trip"><h4>' + trip.title + '</h4></a>' +
                            '</div>' +
                        '</div>' +
                    '</div>';


        return html;
    }

    $("#image").on("change", function(){
       var fd = new FormData();
       fd.append("image", $("#image")[0].files[0]);

       $("#upload_progress").removeClass("hide");
       $("#upload_progress .progress-bar").css({"width" : "50%"});
       $.ajax({
               url: "/trips/" + last_created_id + "/upload",
               type: "POST",
               data: fd,
               processData: false,
               contentType: false,
               success: function(res){
                    renderTripImagesSmall(res.images);
                    $("#upload_progress .progress-bar").css({"width" : "100%"});
               },
               error: function(jqXHR, textStatus, errorThrown ){

               }
       });
    });

    function renderTripImagesSmall(images){
        var html = "";
        for(var i = 0; i < images.length; i++){
            if(images[i] !== default_image_path) html += formImage(images[i]);
        }

        $("#trip_images").html(html);
    }

    setInterval(function(){
        if($("#trip_images").html().trim() !== ""){
            $.ajax({
                url: "/trips/" + last_created_id,
                type: "GET",
                success: function(res){
                    renderTripImagesSmall(res.image_collection);
                }
            });
        }
    }, 500);

    $("#image_upload img").click(function(){
        var src = $(this).attr("src");
        $("#image_to_remove").val(src);

        $("form#remove_trip_img").submit();
    })

    $("#show_custom_label").click(function(){
        if($("#custom_label").hasClass("hide")) $("#custom_label").removeClass("hide")
        else $("#custom_label").addClass("hide");
    })

    $("#trips").on("click", ".open-trip", function(){
        var trip_id = $(this).closest(".trip").attr("id");
        console.log("trip_id: " + trip_id);
        $.ajax({
            url: "/trips/" + trip_id,
            type: "GET",
            success: function(trip){
                $("#trips").html("");
                $("#trip_show").removeClass("hide");

                $("#trip_show .carousel-indicators").html(formCarouselPoints($("#trip_show"), trip));
                $("#trip_show .carousel-inner").html(formCarouselItems(trip));

                $('#trip_show').find('.item').first().addClass('active');
                $('#trip_show').find('li').first().addClass('active');
                $('.carousel').carousel({interval: 6000});
            },
            error: function(jqXHR, textStatus, errorThrown){
            }
        });



    });

    $(".date").datepicker();

    $("#trip_show").on("click", ".close-trip", function(){
        $(".carousel").carousel("pause");
        $("#trip_show").addClass("hide");
        $("#trips").css({"opacity" : "0.5"});
        $("#search_spinner").removeClass("hide");
        getAndRenderTrips();

    });


    function formCarouselItems(trip){
        var html = "";
        for(var i = 0; i < trip.image_collection.length; i++){
            console.log(trip.image_collection[i])
            html += formCarouselItem(trip, i);
        }
        return html;
    }

    function formCarouselItem(trip, index){
        var caption_html = '<div class="carousel-caption pull-left">' +
                           '<h4 class="text-uppercase">' + trip.title + '</h4>' +
                           '<p>' + trip.description + '</p>' +
                           '</div>';

        var html = '<div class="item">' +
                    '<img src="' + trip.image_collection[index] + '">' + caption_html +
                    '</div>';


        return html;
    }

    function formCarouselCaption(trip){
       var html = '<h4>' + trip.title + '</h4>' +
       '<p>' + trip.description + '</p>';

       return html;
    }

    function formCarouselPoints(container, trip){
        var container_id = container.attr("id");
        var html = "";
        for(var i = 0; i < trip.image_collection.length; i++){
            html += '<li data-target="#' + container_id + '" data-slide-to="' + (i+1) + '"></li>';
        }

        return html;
    }

    function formImage(image){
        var html = "<img src=\"" + image + "\" width=\"48\" height=\"48\" title=\"Click to remove\" data-toggle=\"tooltip\" class=\"small-image\" />";
        return html;
    }

});
