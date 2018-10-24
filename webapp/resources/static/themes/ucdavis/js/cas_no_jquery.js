
function checkUsername(e) {
    var username = document.getElementById("username");
    var emailMsg = document.getElementById("emailAddressTyped");
    if(username && username.value.indexOf("@") > -1) {
        if(emailMsg.childNodes.length == 0) {
            emailMsg.innerHTML = "<p>The service you are attempting to access requires using your campus login ID and not your email address.</p>";
        }
        emailMsg.style.display = "inline-block";
    } else {
        emailMsg.style.display = "none";
    }
};

function checkSubmitUsername(e) {
    checkUsername(e);
    var username = document.getElementById("username");
    if(username && username.value.indexOf("@") > -1) {
        return false;
    }
};

function showCookiesDisabled() {
    var cookDis = document.getElementById("cookiesDisabled");
    cookDis.innerHTML = "<h2>Browser cookies disabled</h2><p>Your browser does not accept cookies. Single Sign On WILL NOT WORK.</p>";
    cookDis.style.display = "inline-block";
};