$(document).ready(function(){
    var last_created_id = null;
    $("form#add_trip").submit(function(){
        var url = $(this).attr("action");
        var fd = new FormData();
        fd.append("title", $("#title").val());
        fd.append("description", $("#description").val());



        var labels = $("#check_labels input:checkbox:checked").map(function(){
            return $(this).attr("name").split("_").map(function(item){
                return item[0].toUpperCase() + item.substring(1, item.length);
            }).join(" ");
        });

        if($("#custom_label").val().trim() !== "") labels.push($("#custom_label").val());

        var labels_arr = [];
        for(var i = 0; i < labels.length; i++){ labels_arr.push(labels[i]) }

        $.ajax({
                url: url,
                type: "POST",
                data: JSON.stringify({
                    "title" : $("#title").val(),
                    "description" : $("#description").val(),
                    "labels" : labels_arr,
                    "type" : $("#type").val()
                }),
                processData: false,
                contentType: "application/json",
                success: function(res){
                    $("#success_message").removeClass("hide").html("Trip added. Thanks for contributing");
                    $("#error_message").addClass("hide");
                    $("#image_upload").removeClass("hide");
                    last_created_id = res.id;

                    getAndRenderTrips();
                },
                error: function(jqXHR, textStatus, errorThrown ){
                    $("#error_message").removeClass("hide").html(jqXHR.responseText);
                    $("#success_message").addClass("hide");
                    $("#image_upload").addClass("hide");
                }
        });
        return false;
    });

    var trips_per_row = 0;
    function getAndRenderTrips(){


            $.ajax({
                url: "/trips/browse",
                type: "POST",
                data: JSON.stringify({"sort" : {"updated_timestamp" : "desc"}}),
                processData: false,
                contentType: "application/json",
                success: function(trips){
                    $("#trips").html("");
                    var index = 0;

                    var trips_html = '<div class="row">';
                    for(var i = 0; i < trips.length; i++){
                        trips_html += renderTrip(trips[i]);
                    }
                    trips_html += '<div>';
                    $("#trips").html(trips_html);
                }
            });

    }

    function renderTrip(trip){
        var html = '<div class="col-md-4 trip">' +
                        '<div class="thumbnail">' +
                            '<a href="#"><img src="' + trip.image_collection[0] + '" /></a>' +
                            '<div class="caption">' +
                               '<a href="#"><h4>' + trip.title + '</h4></a>' +
                                '<p>' + trip.description + '</p>' +
                            '</div>' +
                        '</div>' +
                    '</div>';


        return html;
    }

    $("#image").on("change", function(){
       var fd = new FormData();
       fd.append("image", $("#image")[0].files[0]);

       $.ajax({
               url: "/trips/" + last_created_id + "/upload",
               type: "POST",
               data: fd,
               processData: false,
               contentType: false,
               success: function(res){

               },
               error: function(jqXHR, textStatus, errorThrown ){

               }
       });
    });

});
