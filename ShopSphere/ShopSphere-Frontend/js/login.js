const loginBtn = document.getElementById("login-submit");
const resetBtn = document.getElementById("login-reset");
const loginMessage = document.getElementById("login-message");

const API_URL = "http://localhost:8080/ShopSphere-Backend/login";

loginBtn.addEventListener("click", async function (event) {
    event.preventDefault();

    const email = document.getElementById("login-email").value;
    const password = document.getElementById("login-password").value;

    if (!email || !password) {
        loginMessage.textContent = "Please enter both email and password.";
        loginMessage.style.color = "red";
        return;
    }

    const loginCredentials = { email, password };

    try {
        const result = await authenticateUser(loginCredentials);

        localStorage.setItem("token", result.token);
        localStorage.setItem("userName", result.name || "Customer");
        localStorage.setItem("userType", result.userType || "CUSTOMER");
        if (result.birthday) localStorage.setItem("userBirthday", result.birthday);

        loginMessage.textContent = "Login successful! Redirecting...";
        loginMessage.style.color = "green";

        setTimeout(() => {
            window.location.href = "index.html";
        }, 1000);

    } catch (error) {
        loginMessage.textContent = describeError(error);
        loginMessage.style.color = "red";
    }
});

async function authenticateUser(loginCredentials) {
    const response = await fetch(API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(loginCredentials)
    });

    const result = await response.json();

    if (!response.ok) {
        throw new Error(result.message || "Login failed");
    }

    return result;
}

if (resetBtn) {
    resetBtn.addEventListener("click", function () {
        document.getElementById("login-email").value = "";
        document.getElementById("login-password").value = "";
        loginMessage.textContent = "";
    });
}

const toggleLoginPassword = document.getElementById("toggleLoginPassword");
if (toggleLoginPassword) {
    toggleLoginPassword.addEventListener("click", function () {
        const passwordInput = document.getElementById("login-password");
        const isPassword = passwordInput.getAttribute("type") === "password";
        passwordInput.setAttribute("type", isPassword ? "text" : "password");
        this.textContent = isPassword ? "🙈" : "👁️";
        this.title = isPassword ? "Hide password" : "Show password";
    });
}