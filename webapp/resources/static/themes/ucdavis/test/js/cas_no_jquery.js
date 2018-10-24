document.body.onload = function() {
    var impname = document.getElementById("impname");
    var username = document.getElementById("username");
    var password = document.getElementById("password");
    var submit = document.getElementById("submit");
    
    if (impname && impname.value.length == 0)  {
        impname.focus();
    } else if (username && username.value.length == 0) {
        username.focus();
    } else if (password && password.value.length == 0) {
        password.focus();
    } else if (submit) {
        submit.focus();
    }

    if (!navigator.cookieEnabled) {
        showCookiesDisabled();
    }
    testMessage();
};

function testMessage() {
    var testMsg = document.getElementById("testServerMsg");
    if (testMsg) {
        testMsg.innerHTML = "This is a CAS authentication <b>testing server</b>. Do not use this server to authenticate users to production applications."
        testMsg.style.display = "inline-block";
    }
};