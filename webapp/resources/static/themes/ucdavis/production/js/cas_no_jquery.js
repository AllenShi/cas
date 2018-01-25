document.body.onload = function() {
    var username = document.getElementById("username");
    var password = document.getElementById("password");
    var submit = document.getElementById("submit");

    if (username && username.value.length == 0) {
        username.focus();
    } else if (password && password.value.length == 0) {
        password.focus();
    } else if (submit) {
        submit.focus();
    }

    if (!navigator.cookieEnabled) {
        showCookiesDisabled();
    }
};