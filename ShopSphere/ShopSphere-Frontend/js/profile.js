if (!isLoggedIn()) {
    window.location.href = "login.html";
}

document.addEventListener("DOMContentLoaded", () => {
    loadProfileData();
    
    document.getElementById("profileForm").addEventListener("submit", handleProfileUpdate);
    document.getElementById("passwordForm").addEventListener("submit", handlePasswordUpdate);
});

function switchTab(tabName) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.profile-section').forEach(sec => sec.classList.remove('active'));

    if (tabName === 'personal') {
        document.getElementById('tabPersonal').classList.add('active');
        document.getElementById('sectionPersonal').classList.add('active');
    } else if (tabName === 'security') {
        document.getElementById('tabSecurity').classList.add('active');
        document.getElementById('sectionSecurity').classList.add('active');
    }
}

function togglePasswordVisibility(inputId) {
    const input = document.getElementById(inputId);
    if (input.type === "password") {
        input.type = "text";
    } else {
        input.type = "password";
    }
}

async function loadProfileData() {
    try {
        const response = await authFetch(`${BASE_API_URL}/profile`);
        const data = await response.json();
        
        if (response.ok) {
            document.getElementById("email").value = data.email || "";
            document.getElementById("birthday").value = data.birthday || "";
            document.getElementById("name").value = data.name || "";
            document.getElementById("contact").value = data.contact || "";
        } else {
            document.getElementById("profileErrorMsg").textContent = data.message || "Failed to load profile data.";
        }
    } catch (error) {
        document.getElementById("profileErrorMsg").textContent = describeError(error);
    }
}

async function handleProfileUpdate(event) {
    event.preventDefault();
    
    const name = document.getElementById("name").value.trim();
    const contact = document.getElementById("contact").value.trim();
    const errorMsg = document.getElementById("profileErrorMsg");
    const successMsg = document.getElementById("profileSuccessMsg");
    const submitBtn = document.getElementById("btnUpdateProfile");

    errorMsg.textContent = "";
    successMsg.textContent = "";
    document.getElementById("nameError").textContent = "";
    document.getElementById("contactError").textContent = "";

    let hasError = false;
    if (name.length < 3) {
        document.getElementById("nameError").textContent = "Name must be at least 3 characters.";
        hasError = true;
    }
    if (contact.length < 10) {
        document.getElementById("contactError").textContent = "Please enter a valid 10-digit number.";
        hasError = true;
    }

    if (hasError) return;

    submitBtn.disabled = true;
    submitBtn.textContent = "Saving...";

    try {
        const response = await authFetch(`${BASE_API_URL}/profile`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, contact })
        });
        
        const result = await response.json();
        
        if (response.ok) {
            successMsg.textContent = "Profile updated successfully!";
            localStorage.setItem("userName", name);
            updateNavbarState();
        } else {
            errorMsg.textContent = result.message || "Failed to update profile.";
        }
    } catch (error) {
        errorMsg.textContent = describeError(error);
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "Save Changes";
    }
}

async function handlePasswordUpdate(event) {
    event.preventDefault();
    
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;
    const errorMsg = document.getElementById("passwordErrorMsg");
    const successMsg = document.getElementById("passwordSuccessMsg");
    const submitBtn = document.getElementById("btnUpdatePassword");

    errorMsg.textContent = "";
    successMsg.textContent = "";
    document.getElementById("newPasswordError").textContent = "";
    document.getElementById("confirmPasswordError").textContent = "";

    if (newPassword !== confirmPassword) {
        document.getElementById("confirmPasswordError").textContent = "Passwords do not match.";
        return;
    }

    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%]).{8,}$/;
    if (!passwordRegex.test(newPassword)) {
        document.getElementById("newPasswordError").textContent = "Password must contain 8+ characters, a number, uppercase, lowercase, and special character.";
        return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = "Updating...";

    try {
        const response = await authFetch(`${BASE_API_URL}/profile/password`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ currentPassword, newPassword })
        });
        
        const result = await response.json();
        
        if (response.ok) {
            successMsg.textContent = "Password updated successfully!";
            document.getElementById("passwordForm").reset();
        } else {
            errorMsg.textContent = result.message || "Failed to update password.";
        }
    } catch (error) {
        errorMsg.textContent = describeError(error);
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "Update Password";
    }
}
