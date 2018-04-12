onmessage = function (e) {
    var xhr = new XMLHttpRequest();
    xhr.onload = function () {
        if (xhr.status === 200) {
            postMessage(JSON.parse(xhr.responseText).content);
        }
        else {
            console.log('Request failed. Returned status of ' + xhr.status);
        }
    };

    xhr.open('GET', '/api/greeting?from=' + e.data.from);
    xhr.send();
};