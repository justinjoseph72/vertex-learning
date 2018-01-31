function bid() {
    console.log("from the test");
    var newPrice = document.getElementById('my_bid_value').value;

    var xmlhttp = (window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP");
    xmlhttp.onreadystatechange = function () {
        if (xmlhttp.readyState == 4) {
            if (xmlhttp.status != 200) {
                document.getElementById('error_message').innerHTML = 'Sorry, something went wrong';
            }
        }
    };
    xmlhttp.open("patch", "http://localhost:8080/api/acutions/" + auction_id);
    xmlhttp.setRequestHeader("Content-Type", "application/json");
    xmlhttp.send(JSON.stringify({price: newPrice}));
};

function registerHandlerForUpdateCurrentPricesAndFeed() {
    var eventBus = new EventBus('http://localhost:8080/eventbus');
    eventBus.onopen = function () {
        eventBus.registerHandler('auction.' + auction_id, function (error, message) {
            document.getElementById('current_price').innerHTML = JSON.parse(message.body).price;
            document.getElementById('feed').value += 'New offer: ' + JSON.parse(message.body).price + '\n';
        });
    }
};