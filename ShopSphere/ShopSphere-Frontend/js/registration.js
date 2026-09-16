const registrationForm = document.getElementById("registrationForm");
const successMessage = document.getElementById("successMessage");
const serverError = document.getElementById("serverError");
const birthdayInput = document.getElementById("birthday");

const registrationError = {
  name: document.getElementById("nameError"),
  email: document.getElementById("emailError"),
  contact: document.getElementById("contactError"),
  birthday: document.getElementById("birthdayError"),
  password: document.getElementById("passwordError")
};

function formatDateLocal(d) {
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

if (birthdayInput) {
  birthdayInput.min = formatDateLocal(getDateYearsAgo(100));
  birthdayInput.max = formatDateLocal(getDateYearsAgo(18));
}

registrationForm.addEventListener("submit", async function(event){
event.preventDefault();
successMessage.textContent = "";
serverError.textContent = "";

const registrationData = {
  name: document.getElementById("name").value,
  email: document.getElementById("email").value,
  contact: document.getElementById("contact").value,
  birthday: document.getElementById("birthday").value,
  password: document.getElementById("password").value
};

if(validateUserData(registrationData)){
  try {
    const result = await sendRegistrationDetails(registrationData);
    successMessage.textContent = result.message || "Registration successful! Redirecting to login...";
    successMessage.style.color = "green";
    registrationForm.reset();
    setTimeout(() => {
      window.location.href = "login.html";
    }, 1500);
  } catch (error) {
    serverError.textContent = describeError(error);
  }
}

});

function validateUserData(registrationData){
  const emailPattern = /^[a-zA-Z0-9+._-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}$/;
  const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%]).{8,}$/;
  let isValid = true;

  registrationError.name.textContent = "";
  registrationError.contact.textContent = "";
  registrationError.password.textContent = "";
  registrationError.email.textContent = "";
  registrationError.birthday.textContent = "";

  if (registrationData.name.trim().length < 3) {
    registrationError.name.textContent = "Name cannot be less than 3 characters";
    isValid = false;
  }

  if (registrationData.contact.trim().length < 10) {
    registrationError.contact.textContent = "Mobile number must be at least 10 digits";
    isValid = false;
  }

  if (!passwordPattern.test(registrationData.password)) {
    registrationError.password.textContent = "Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character (!@#$%).";
    isValid = false;
  }

  if (!emailPattern.test(registrationData.email)) {
    registrationError.email.textContent = "Enter a valid email address";
    isValid = false;
  }

  const birthday = parseInputDate(registrationData.birthday);
  const minimumDate = getDateYearsAgo(100);
  const maximumDate = getDateYearsAgo(18);

  if (!birthday || birthday < minimumDate || birthday > maximumDate) {
    registrationError.birthday.textContent = "Birthday must be between 18 years and 100 years";
    isValid = false;
  }

  return isValid;
}

function getDateYearsAgo(years) {
  const date = new Date();
  date.setFullYear(date.getFullYear() - years);
  date.setHours(0, 0, 0, 0);
  return date;
}

function parseInputDate(value) {
  const dateParts = value.split("-").map(Number);

  if (dateParts.length !== 3 || dateParts.some(Number.isNaN)) {
    return null;
  }

  const [year, month, day] = dateParts;
  const date = new Date(year, month - 1, day);

  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }

  date.setHours(0, 0, 0, 0);
  return date;
}

async function sendRegistrationDetails(registrationData){
  const response = await fetch("http://localhost:8080/ShopSphere-Backend/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(registrationData)
  });

  const result = await response.json();

  if (!response.ok) {
    throw new Error(result.message || "Registration failed");
  }

  return result;
}

const toggleRegPassword = document.getElementById("toggleRegPassword");
if (toggleRegPassword) {
  toggleRegPassword.addEventListener("click", function () {
    const passwordInput = document.getElementById("password");
    const isPassword = passwordInput.getAttribute("type") === "password";
    passwordInput.setAttribute("type", isPassword ? "text" : "password");
    this.textContent = isPassword ? "🙈" : "👁️";
    this.title = isPassword ? "Hide password" : "Show password";
  });
}
