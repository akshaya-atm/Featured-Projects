// ShopSphere Dynamic Hero Carousel & Home Page Logic

const CATEGORIES_URL = "http://localhost:8080/ShopSphere-Backend/categories";
const PRODUCTS_URL = "http://localhost:8080/ShopSphere-Backend/products";

let currentSlide = 0;
let totalSlides = 0;
let carouselInterval;

const carouselTrack = document.getElementById("carouselTrack");
const carouselPagination = document.getElementById("carouselPagination");
const prevSlideBtn = document.getElementById("prevSlideBtn");
const nextSlideBtn = document.getElementById("nextSlideBtn");
const homeCategoriesGrid = document.getElementById("homeCategoriesGrid");
const homeDealsGrid = document.getElementById("homeDealsGrid");

document.addEventListener("DOMContentLoaded", () => {
  initHeroCarousel();
  loadCategories();
  loadTopDeals();
});

const DISCOUNTS_URL = "http://localhost:8080/ShopSphere-Backend/discounts";

async function initHeroCarousel() {
  const slidesData = [];

  let dbDiscounts = [];
  try {
    const res = await fetch(DISCOUNTS_URL);
    if (res.ok) {
      dbDiscounts = await res.json();
    }
  } catch (e) {
    console.error("Could not fetch DB discounts for hero carousel:", e);
  }

  // 1. Birthday Discount Slide (only if user is logged in and today is their birthday)
  const token = localStorage.getItem("token");
  const userBirthdayStr = localStorage.getItem("userBirthday");
  const isBirthdayToday = Boolean(token) && checkIsBirthdayToday(userBirthdayStr);

  // Jackson serializes Java's isBirthdayOnly getter as "birthdayOnly" in the JSON response.
  const birthdayDisc = dbDiscounts.find(d => d.birthdayOnly || (d.name && d.name.toLowerCase().includes("birthday")));
  if (isBirthdayToday && birthdayDisc) {
    const discValStr = birthdayDisc.discountType === "PERCENTAGE" ? `${birthdayDisc.discountValue}% OFF` : `₹${birthdayDisc.discountValue} OFF`;
    slidesData.push({
      badgeClass: "badge-birthday",
      badgeText: "🎂 Special Birthday Celebration",
      title: `Happy Birthday! Enjoy ${discValStr} Your Order`,
      subtitle: `${birthdayDisc.name} - Exclusive birthday savings automatically applied at checkout!`,
      ctaText: "Claim Birthday Deal",
      ctaLink: "products.html",
      bgImg: "https://images.unsplash.com/photo-1513151233558-d860c5398176?auto=format&fit=crop&w=1200&q=80"
    });
  }

  // 2. Area / Pincode Discount Slide (Loaded dynamically from Admin DB Data)
  const areaDisc = dbDiscounts.find(d => d.targetPincode && d.targetPincode.trim().length > 0);
  if (areaDisc) {
    const discValStr = areaDisc.discountType === "PERCENTAGE" ? `${areaDisc.discountValue}% Extra Savings` : `₹${areaDisc.discountValue} Flat Savings`;
    slidesData.push({
      badgeClass: "badge-area",
      badgeText: "🚚 Regional Delivery Offer",
      title: `Pincode ${areaDisc.targetPincode}: ${discValStr}`,
      subtitle: `${areaDisc.name} - Location special for ${areaDisc.targetPincode} orders over ₹${areaDisc.minOrderAmount || 0}.`,
      ctaText: "Shop Regional Items",
      ctaLink: "products.html",
      bgImg: "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=1200&q=80"
    });
  }

  // 3. Product Flash Sale Discount Slide (Loaded dynamically from Admin DB Data)
  const productDisc = dbDiscounts.find(d => d.targetScope === "PRODUCT");
  if (productDisc) {
    const discValStr = productDisc.discountType === "PERCENTAGE" ? `${productDisc.discountValue}% OFF` : `₹${productDisc.discountValue} OFF`;
    slidesData.push({
      badgeClass: "badge-birthday",
      badgeText: "🔥 Limited Time Flash Sale",
      title: `${productDisc.name} - ${discValStr}`,
      subtitle: "Exclusive product discount configured live by store admin. Grab it before stock runs out!",
      ctaText: "Shop Flash Deals",
      ctaLink: "products.html",
      bgImg: "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=1200&q=80"
    });
  }

  // 4. Featured Category Extravaganza Slide
  slidesData.push({
    badgeClass: "badge-category",
    badgeText: "🥗 Category Extravaganza",
    title: "Fresh Farm Produce & Organic Bio",
    subtitle: "Stock up on organic fruits, farm-fresh vegetables, and greens with massive category savings.",
    ctaText: "Explore Catalog",
    ctaLink: "products.html",
    bgImg: "https://images.unsplash.com/photo-1610832958506-aa56368176cf?auto=format&fit=crop&w=1200&q=80"
  });

  renderCarouselSlides(slidesData);
}

function checkIsBirthdayToday(birthdayStr) {
  if (!birthdayStr) return false;
  const today = new Date();
  const birthDate = new Date(birthdayStr);
  return today.getMonth() === birthDate.getMonth() && today.getDate() === birthDate.getDate();
}

function renderCarouselSlides(slides) {
  totalSlides = slides.length;
  currentSlide = 0;
  if (carouselTrack) {
    carouselTrack.innerHTML = slides.map(slide => `
      <div class="carousel-slide">
        <div class="slide-left-content">
          <span class="slide-badge ${slide.badgeClass}">${slide.badgeText}</span>
          <h1 class="slide-title">${slide.title}</h1>
          <p class="slide-subtitle">${slide.subtitle}</p>
          <a href="${slide.ctaLink}" class="slide-cta-btn">${slide.ctaText} &rarr;</a>
        </div>
        <div class="slide-right-image">
          <img src="${slide.bgImg}" alt="${slide.title}">
        </div>
      </div>
    `).join("");
  }

  renderPaginationDots();
  updateCarouselPosition();
  startAutoSlide();
}

function renderPaginationDots() {
  if (!carouselPagination) return;
  carouselPagination.innerHTML = "";
  for (let i = 0; i < totalSlides; i++) {
    const dot = document.createElement("div");
    dot.className = `dot ${i === 0 ? 'active' : ''}`;
    dot.addEventListener("click", () => goToSlide(i));
    carouselPagination.appendChild(dot);
  }
}

function goToSlide(index) {
  currentSlide = index;
  updateCarouselPosition();
  resetAutoSlide();
}

function updateCarouselPosition() {
  if (carouselTrack) carouselTrack.style.transform = `translateX(-${currentSlide * 100}%)`;
  document.querySelectorAll(".dot").forEach((dot, idx) => {
    dot.classList.toggle("active", idx === currentSlide);
  });
}

function nextSlide() {
  if (totalSlides <= 1) return;
  currentSlide = (currentSlide + 1) % totalSlides;
  updateCarouselPosition();
}

function prevSlide() {
  if (totalSlides <= 1) return;
  currentSlide = (currentSlide - 1 + totalSlides) % totalSlides;
  updateCarouselPosition();
}

if (prevSlideBtn) prevSlideBtn.addEventListener("click", () => { prevSlide(); resetAutoSlide(); });
if (nextSlideBtn) nextSlideBtn.addEventListener("click", () => { nextSlide(); resetAutoSlide(); });

function startAutoSlide() {
  if (totalSlides <= 1) return;
  clearInterval(carouselInterval);
  carouselInterval = setInterval(nextSlide, 5000);
}

function stopAutoSlide() {
  clearInterval(carouselInterval);
}

function resetAutoSlide() {
  stopAutoSlide();
  startAutoSlide();
}

const carouselContainer = document.querySelector(".carousel-container");
if (carouselContainer) {
  carouselContainer.addEventListener("mouseenter", stopAutoSlide);
  carouselContainer.addEventListener("mouseleave", startAutoSlide);
}

async function loadCategories() {
  if (!homeCategoriesGrid) return;
  try {
    const res = await fetch(CATEGORIES_URL);
    if (!res.ok) return;
    const categories = await res.json();
    
    const uniqueCategories = [];
    const seenNames = new Set();
    categories.forEach(cat => {
      if (!seenNames.has(cat.categoryName)) {
        seenNames.add(cat.categoryName);
        uniqueCategories.push(cat);
      }
    });

    homeCategoriesGrid.innerHTML = uniqueCategories.map(cat => `
      <a href="products.html?category=${cat.categoryId}" class="cat-card">
        <img src="${cat.categoryImageUrl || 'https://images.unsplash.com/photo-1542838132-92c53300491e?w=400'}" alt="${cat.categoryName}" class="cat-img">
        <div class="cat-info">
          <h3 class="cat-title">${cat.categoryName}</h3>
          <p class="cat-desc">${cat.categoryDescription || 'High quality products'}</p>
        </div>
      </a>
    `).join("");
  } catch (err) {
    console.error("Error loading categories:", err);
  }
}

async function loadTopDeals() {
  if (!homeDealsGrid) return;
  try {
    const res = await fetch(PRODUCTS_URL);
    if (!res.ok) return;
    const products = await res.json();
    
    const topDeals = products.slice(0, 4);

    homeDealsGrid.innerHTML = topDeals.map(prod => `
      <div class="product-card" style="background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 16px; overflow: hidden; padding: 1.25rem; box-shadow: var(--shadow-sm); position: relative;">
        ${prod.discountPercentage ? `
          <span style="position: absolute; top: 0.85rem; left: 0.85rem; background: #fef3c7; color: #d97706; font-size: 0.7rem; font-weight: 800; padding: 0.25rem 0.55rem; border-radius: 6px; border: 1px solid #fcd34d; z-index: 2;">
            ${prod.discountPercentage}% OFF
          </span>
        ` : ''}
        <img src="${prod.imageUrl}" alt="${prod.name}" style="width: 100%; height: 130px; object-fit: contain; background: #f1f5f9; border-radius: 8px;">
        <h4 style="margin: 0.75rem 0 0.25rem 0; font-size: 1rem; color: #0f172a;">${prod.name}</h4>
        <p style="font-size: 0.8rem; color: #64748b; margin-bottom: 0.75rem;">${(prod.categories && prod.categories.length > 0) ? prod.categories[0].categoryName : 'General'}</p>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            ${prod.discountedPrice ? `
              <strong style="font-size: 1.15rem; color: #0f172a;">₹${prod.discountedPrice}</strong>
              <span style="font-size: 0.8rem; color: #94a3b8; text-decoration: line-through; margin-left: 0.35rem;">₹${prod.price}</span>
            ` : `
              <strong style="font-size: 1.1rem; color: #0f172a;">₹${prod.price}</strong>
            `}
          </div>
          <a href="products.html" style="font-size: 0.8rem; color: var(--primary-hover); font-weight: 700; text-decoration: none;">View Deal &rarr;</a>
        </div>
      </div>
    `).join("");
  } catch (err) {
    console.error("Error loading top deals:", err);
  }
}
