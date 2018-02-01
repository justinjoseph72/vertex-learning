var auction_id = 1;

function init() {
    loadCurrentPrice();
    registerHandlerForUpdateCurrentPricesAndFeed();
};

function loadCurrentPrice() {
    var xmlhttp = (window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP");
    xmlhttp.onreadystatechange = function () {
        if (xmlhttp.readyState == 4) {
            if (xmlhttp.status == 200) {
                document.getElementById('current_price').innerHTML = 'EUR' + JSON.parse(xmlhttp.responseText).price.toFixed(2);
            } else {
                document.getElementById('current_price').innerHTML = 'EUR 0.00';
            }
        }
    };
    xmlhttp.open("GET", "http://localhost:8080/api/auctions/" + auction_id);
    xmlhttp.send();
};

function bid() {
    console.log("from the test");
    var newPrice = document.getElementById('my_bid_value').value;
    console.log(JSON.stringify({price: newPrice}));
    var xmlhttp = (window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP");
    xmlhttp.onreadystatechange = function () {
        if (xmlhttp.readyState == 4) {
            console.log('patch status is ' + xmlhttp.status);
            if (xmlhttp.status != 200) {
                document.getElementById('error_message').innerHTML = 'Sorry, something went wrong';
            }
        }
    };
    console.log("starting patch process with auction id is " + auction_id);
    xmlhttp.open("PATCH", "http://localhost:8080/api/auctions/" + auction_id,true);
    xmlhttp.setRequestHeader("Content-Type", "application/json");
    xmlhttp.send(JSON.stringify({price: newPrice}));
    console.log("ending patch process");
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