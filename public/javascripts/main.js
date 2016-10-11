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
        console.log(labels);

        if($("#custom_label").val().trim() !== "") labels.push($("#custom_label").val());

        var labels_arr = [];
        for(var i = 0; i < labels.length; i++){ labels_arr.push(labels[i]) }
        console.log(labels_arr);

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
                },
                error: function(jqXHR, textStatus, errorThrown ){
                    $("#error_message").removeClass("hide").html(jqXHR.responseText);
                    $("#success_message").addClass("hide");
                    $("#image_upload").addClass("hide");
                }
        });
        return false;
    });


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
