const express = require('express');
const fs = require('fs');
const path = require('path');
const fetch = require('node-fetch');

const app = express();
const PORT = process.env.PORT || 3000;

const LANGS = ['en', 'cn'];
const DEFAULT_LANG = 'en';
const BACKEND_URL = 'http://localhost:8080';
/** 规范 URL 与 hreflang 使用的站点根（无末尾斜杠） */
const SITE_ORIGIN = (process.env.SITE_ORIGIN || 'https://musilabaudio.com').replace(/\/$/, '');
/** sitemap 中 loc 使用的绝对根（默认 https://www.musilabaudio.com；可用 SITEMAP_ORIGIN 覆盖） */
const SITEMAP_ORIGIN = (
  process.env.SITEMAP_ORIGIN || process.env.SITE_ORIGIN || 'https://www.musilabaudio.com'
).replace(/\/$/, '');

/** 首页「热门/精选」区块最多展示条数（完整列表见 /${lang}/products） */
const HOME_PRODUCTS_LIMIT = 6;
/** 产品详情页「相关产品」最多条数 */
const RELATED_PRODUCTS_LIMIT = 6;

/** 透明 1×1 GIF，无图时不请求不存在的静态资源 */
const EMPTY_IMG =
  'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';

/** 接口 data 可能是数组或分页对象，统一成数组（避免 slice/map 失效） */
function normalizeProductList(raw) {
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw;
  if (typeof raw === 'object') {
    if (Array.isArray(raw.records)) return raw.records;
    if (Array.isArray(raw.list)) return raw.list;
    if (Array.isArray(raw.data)) return raw.data;
    if (Array.isArray(raw.rows)) return raw.rows;
  }
  return [];
}

/** 分类列表接口 data 形态兼容（与 normalizeProductList 类似） */
function normalizeCategoryList(raw) {
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw;
  if (typeof raw === 'object') {
    if (Array.isArray(raw.records)) return raw.records;
    if (Array.isArray(raw.list)) return raw.list;
    if (Array.isArray(raw.data)) return raw.data;
    if (Array.isArray(raw.rows)) return raw.rows;
  }
  return [];
}

/**
 * 与 admin 页面一致：浏览器用当前站点根路径访问 /uploads/...（由 Nginx 反代到后端静态）
 * 不把图片 host 写死为 localhost:8080
 */
function publicImgSrc(u) {
  const t = String(u == null ? '' : u).trim();
  if (!t) return '';
  if (/^https?:\/\//i.test(t)) return t;
  if (t.startsWith('/uploads/')) return t;
  if (t.startsWith('uploads/')) return `/${t}`;
  if (t.startsWith('/')) return t;
  return `/uploads/${t}`;
}

// imageList：逗号分隔相对路径；imageUrl：主图相对路径（如 /uploads/product/.../main.jpg）
function parseImageList(imageListStr, imageUrl) {
  const listStr =
    imageListStr == null || imageListStr === ''
      ? ''
      : typeof imageListStr === 'string'
        ? imageListStr
        : Array.isArray(imageListStr)
          ? imageListStr.join(',')
          : String(imageListStr);
  const urlStr =
    imageUrl == null || imageUrl === ''
      ? ''
      : typeof imageUrl === 'string'
        ? imageUrl
        : String(imageUrl);

  if (typeof listStr === 'string' && listStr.trim() !== '') {
    const images = listStr.split(',').map((img) => img.trim()).filter((img) => img !== '');
    if (images.length > 0) {
      return images.map((img) => publicImgSrc(img));
    }
  }

  if (typeof urlStr === 'string' && urlStr.trim() !== '') {
    return [publicImgSrc(urlStr.trim())];
  }

  return [];
}

/** 列表卡片 / 关联区主图（第一张），与 admin 同源相对路径 */
function productCardImageSrc(product) {
  const paths = parseImageList(product.imageList, product.imageUrl);
  return paths.length ? paths[0] : '';
}

/** 详情页画廊：多张，与 admin 同源相对路径 */
function collectProductGalleryUrls(product) {
  return parseImageList(product.imageList, product.imageUrl).filter(Boolean);
}

function stripHtmlForMeta(s) {
  return String(s || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function escapeHtmlAttr(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** 各语言版本路径（含查询串），用于 canonical 与 hreflang */
function pathsForSeo(pageName, query, product) {
  const q = query || {};
  const catId = q.categoryId;
  const catSuffix =
    catId !== undefined && catId !== null && catId !== ''
      ? `?categoryId=${encodeURIComponent(String(catId))}`
      : '';
  if (pageName === 'index') {
    return { en: `/en${catSuffix}`, cn: `/cn${catSuffix}` };
  }
  if (pageName === 'products') {
    return { en: `/en/products${catSuffix}`, cn: `/cn/products${catSuffix}` };
  }
  if (pageName === 'product-detail') {
    const pid =
      product && product.id != null
        ? product.id
        : q.id != null && q.id !== ''
          ? q.id
          : '';
    const idSuffix = pid !== '' && pid != null ? `?id=${encodeURIComponent(String(pid))}` : '';
    return {
      en: `/en/products/detail${idSuffix}`,
      cn: `/cn/products/detail${idSuffix}`
    };
  }
  return { en: '/en', cn: '/cn' };
}

function generateSocialMeta(pageName, lang, query, data) {
  const isEn = lang === 'en';
  const paths = pathsForSeo(pageName, query, data.product);
  const url = SITE_ORIGIN + (lang === 'cn' ? paths.cn : paths.en);
  let title, description, image;

  if (pageName === 'index') {
    title = isEn ? 'Musilab Audio - Premium Wireless Bluetooth Speakers & Home Audio Systems' : 'Musilab Audio - 高品质无线蓝牙音箱和家庭音响系统';
    description = isEn ? 'Musilab Audio specializes in high-quality wireless audio solutions.' : 'Musilab Audio专注于高品质无线音频解决方案。';
    image = `${SITE_ORIGIN}/img/infinite-loop-01.jpg`;
  } else if (pageName === 'products') {
    title = isEn ? 'All Products - Musilab Audio' : '全部产品 - Musilab Audio';
    description = isEn ? 'Browse our full range of high-quality wireless Bluetooth speakers and audio devices.' : '浏览我们全系列高品质无线蓝牙音箱和音频设备。';
    image = `${SITE_ORIGIN}/img/infinite-loop-01.jpg`;
  } else if (pageName === 'product-detail' && data.product) {
    const p = data.product;
    title = `${stripHtmlForMeta(p.name || '')} - Musilab Audio`;
    description = stripHtmlForMeta(p.description || '').slice(0, 320) || title;
    const imgs = parseImageList(p.imageList, p.imageUrl);
    image = imgs.length ? (imgs[0].startsWith('http') ? imgs[0] : SITE_ORIGIN + imgs[0]) : `${SITE_ORIGIN}/logo.svg`;
  } else {
    title = 'Musilab Audio';
    description = isEn ? 'High-quality wireless audio equipment from Musilab Audio.' : 'Musilab Audio，专业无线音频设备。';
    image = `${SITE_ORIGIN}/logo.svg`;
  }

  const ogLocale = lang === 'cn' ? 'zh_CN' : 'en_US';
  const ogType = pageName === 'product-detail' ? 'product' : 'website';

  return [
    `<meta property="og:title" content="${escapeHtmlAttr(title)}">`,
    `<meta property="og:description" content="${escapeHtmlAttr(description)}">`,
    `<meta property="og:image" content="${escapeHtmlAttr(image)}">`,
    `<meta property="og:url" content="${escapeHtmlAttr(url)}">`,
    `<meta property="og:type" content="${ogType}">`,
    `<meta property="og:site_name" content="Musilab Audio">`,
    `<meta property="og:locale" content="${ogLocale}">`,
    `<meta name="twitter:card" content="summary_large_image">`,
    `<meta name="twitter:title" content="${escapeHtmlAttr(title)}">`,
    `<meta name="twitter:description" content="${escapeHtmlAttr(description)}">`,
    `<meta name="twitter:image" content="${escapeHtmlAttr(image)}">`
  ].join('\n    ');
}

function generateJsonLd(pageName, lang, query, data) {
  const isEn = lang === 'en';
  const paths = pathsForSeo(pageName, query, data.product);
  const url = SITE_ORIGIN + (lang === 'cn' ? paths.cn : paths.en);
  const blocks = [];

  // Organization (all pages)
  blocks.push({
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Musilab Audio',
    url: SITE_ORIGIN,
    logo: `${SITE_ORIGIN}/logo.svg`,
    contactPoint: {
      '@type': 'ContactPoint',
      telephone: '+86-769-38996619',
      contactType: 'sales',
      email: 'kenny@musilab.com.cn'
    },
    address: {
      '@type': 'PostalAddress',
      streetAddress: 'No.5 Huanwei Street, Fugang, Qingxi Town',
      addressLocality: 'Dongguan',
      addressRegion: 'Guangdong',
      addressCountry: 'CN'
    }
  });

  // BreadcrumbList (products + product-detail)
  if (pageName === 'products' || pageName === 'product-detail') {
    const breadcrumbItems = [
      { '@type': 'ListItem', position: 1, name: isEn ? 'Home' : '首页', item: SITE_ORIGIN + '/' + lang }
    ];
    if (pageName === 'product-detail') {
      breadcrumbItems.push({ '@type': 'ListItem', position: 2, name: isEn ? 'Products' : '产品中心', item: SITE_ORIGIN + '/' + lang + '/products' });
      breadcrumbItems.push({ '@type': 'ListItem', position: 3, name: data.product ? stripHtmlForMeta(data.product.name || '') : (isEn ? 'Product' : '产品') });
    } else {
      breadcrumbItems.push({ '@type': 'ListItem', position: 2, name: isEn ? 'Products' : '产品中心' });
    }
    blocks.push({ '@context': 'https://schema.org', '@type': 'BreadcrumbList', itemListElement: breadcrumbItems });
  }

  // Product schema
  if (pageName === 'product-detail' && data.product) {
    const p = data.product;
    const imgs = parseImageList(p.imageList, p.imageUrl);
    const productImages = imgs.map(img => img.startsWith('http') ? img : SITE_ORIGIN + img);
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: p.name || '',
      description: stripHtmlForMeta(p.description || '').slice(0, 500),
      image: productImages.length ? productImages : undefined,
      sku: String(p.id),
      brand: { '@type': 'Brand', name: 'Musilab Audio' },
      offers: {
        '@type': 'Offer',
        price: String(p.price || 0),
        priceCurrency: 'CNY',
        availability: 'https://schema.org/InStock',
        url: url
      }
    });
  }

  // WebSite (homepage only)
  if (pageName === 'index') {
    blocks.push({
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: 'Musilab Audio',
      url: SITE_ORIGIN,
      potentialAction: {
        '@type': 'SearchAction',
        target: `${SITE_ORIGIN}/search?q={search_term_string}`,
        'query-input': 'required name=search_term_string'
      }
    });
  }

  return blocks.map(b => `<script type="application/ld+json">${JSON.stringify(b)}</script>`).join('\n    ');
}

function generateBreadcrumb(lang, pageName, data) {
  const isEn = lang === 'en';
  if (pageName === 'index') return '';
  let html = '<nav aria-label="breadcrumb" class="breadcrumb-nav"><ol class="breadcrumb">';
  html += `<li class="breadcrumb-item"><a href="/${lang}">${isEn ? 'Home' : '首页'}</a></li>`;
  if (pageName === 'product-detail') {
    html += `<li class="breadcrumb-item"><a href="/${lang}/products">${isEn ? 'Products' : '产品中心'}</a></li>`;
    html += `<li class="breadcrumb-item active">${data.product ? escapeHtmlAttr(data.product.name || '') : (isEn ? 'Product' : '产品')}</li>`;
  } else if (pageName === 'products') {
    html += `<li class="breadcrumb-item active">${isEn ? 'Products' : '产品中心'}</li>`;
  }
  html += '</ol></nav>';
  return html;
}

function applySeoMeta(template, lang, pageName, query, data) {
  const isEn = lang === 'en';
  const paths = pathsForSeo(pageName, query, data.product);
  const canonicalHref = SITE_ORIGIN + (lang === 'cn' ? paths.cn : paths.en);
  const hreflangBlock = [
    `<link rel="alternate" hreflang="en" href="${SITE_ORIGIN}${paths.en}">`,
    `<link rel="alternate" hreflang="zh-CN" href="${SITE_ORIGIN}${paths.cn}">`,
    `<link rel="alternate" hreflang="x-default" href="${SITE_ORIGIN}${paths.en}">`
  ].join('\n    ');

  let title;
  let description;
  let keywords;

  if (pageName === 'index') {
    if (isEn) {
      title = 'Musilab Audio - Premium Wireless Bluetooth Speakers & Home Audio Systems';
      description =
        'Musilab Audio specializes in high-quality wireless audio solutions. Shop our range of portable Bluetooth speakers, home audio systems and more.';
      keywords = 'bluetooth speaker, wireless speaker, portable speaker, home audio, Musilab';
    } else {
      title = 'Musilab Audio - 高品质无线蓝牙音箱和家庭音响系统';
      description =
        'Musilab Audio专注于高品质无线音频解决方案。选购我们的便携式蓝牙音箱、家庭音响系统等产品。';
      keywords = '蓝牙音箱, 无线音箱, 便携式音箱, 家庭音响, Musilab';
    }
  } else if (pageName === 'products') {
    if (isEn) {
      title = 'All Products - Musilab Audio';
      description = 'Browse our full range of high-quality wireless Bluetooth speakers and audio devices.';
      keywords = 'bluetooth speaker, wireless speaker, all products, Musilab Audio';
    } else {
      title = '全部产品 - Musilab Audio';
      description = '浏览我们全系列高品质无线蓝牙音箱和音频设备。';
      keywords = '蓝牙音箱, 无线音箱, 全部产品, Musilab';
    }
  } else if (pageName === 'product-detail' && data.product) {
    const p = data.product;
    const name = stripHtmlForMeta(p.name || '') || (isEn ? 'Product' : '产品');
    const descRaw = stripHtmlForMeta(p.description || '');
    description =
      descRaw.slice(0, 320) ||
      (isEn ? 'Product details at Musilab Audio.' : 'Musilab Audio产品详情。');
    if (isEn) {
      title = `${name} - Musilab Audio`;
      keywords = `${name}, bluetooth speaker, portable speaker, home audio, Musilab`;
    } else {
      title = `${name} - Musilab Audio`;
      keywords = `${name}, 蓝牙音箱, 无线音箱, 家庭音响, Musilab`;
    }
  } else {
    title = isEn ? 'Musilab Audio' : 'Musilab Audio';
    description = isEn
      ? 'High-quality wireless audio equipment from Musilab Audio.'
      : 'Musilab Audio，专业无线音频设备。';
    keywords = isEn ? 'Musilab, wireless audio' : 'Musilab, 无线音频';
  }

  const socialMeta = generateSocialMeta(pageName, lang, query, data);
  const jsonLd = generateJsonLd(pageName, lang, query, data);
  const breadcrumb = generateBreadcrumb(lang, pageName, data);

  return template
    .replace(/\{\{META_TITLE\}\}/g, escapeHtmlAttr(title))
    .replace(/\{\{META_DESCRIPTION\}\}/g, escapeHtmlAttr(description))
    .replace(/\{\{META_KEYWORDS\}\}/g, escapeHtmlAttr(keywords))
    .replace(/\{\{META_CANONICAL\}\}/g, escapeHtmlAttr(canonicalHref))
    .replace(/\{\{HREFLANG_LINKS\}\}/g, hreflangBlock)
    .replace(/\{\{SOCIAL_META\}\}/g, socialMeta)
    .replace(/\{\{JSON_LD\}\}/g, jsonLd)
    .replace(/\{\{BREADCRUMB\}\}/g, breadcrumb);
}

const templates = {};

function loadTemplates() {
  LANGS.forEach(lang => {
    const langDir = path.join(__dirname, lang);
    templates[lang] = {};
    
    // 检查语言目录是否存在
    if (fs.existsSync(langDir)) {
      // 尝试读取各个模板文件，如果失败则记录错误但继续
      try {
        templates[lang].index = fs.readFileSync(path.join(langDir, 'index.html'), 'utf8');
      } catch (e) {
        console.error(`Warning: Could not load ${lang}/index.html, using fallback`);
        templates[lang].index = '';
      }
      
      try {
        templates[lang].products = fs.readFileSync(path.join(langDir, 'products.html'), 'utf8');
      } catch (e) {
        console.error(`Warning: Could not load ${lang}/products.html, using fallback`);
        templates[lang].products = '';
      }
      
      try {
        templates[lang]['product-detail'] = fs.readFileSync(path.join(langDir, 'product-detail.html'), 'utf8');
      } catch (e) {
        console.error(`Warning: Could not load ${lang}/product-detail.html, using fallback`);
        templates[lang]['product-detail'] = '';
      }
    } else {
      console.error(`Warning: Language directory ${langDir} does not exist`);
      templates[lang] = {
        index: '',
        products: '',
        'product-detail': ''
      };
    }
  });
}

loadTemplates();

function getLangFromUrl(url) {
  const parts = url.split('/');
  if (parts.length >= 2 && LANGS.includes(parts[1])) {
    return parts[1];
  }
  return DEFAULT_LANG;
}

function getPageName(url, lang) {
  const urlWithoutLang = url.replace(`/${lang}`, '');
  
  if (urlWithoutLang === '/' || urlWithoutLang === '' || urlWithoutLang === '/index.html') {
    return 'index';
  }
  
  if (urlWithoutLang.startsWith('/products')) {
    if (urlWithoutLang.includes('detail') || urlWithoutLang.includes('id=')) {
      return 'product-detail';
    }
    return 'products';
  }
  
  return 'index';
}

async function fetchProductData(productId = null) {
  try {
    const response = await fetch(`${BACKEND_URL}/api/product/${productId || 'list'}`);
    return await response.json();
  } catch (error) {
    console.error('Failed to fetch product data:', error);
    return { code: 500, data: null };
  }
}

async function fetchCategoryData() {
  try {
    const response = await fetch(`${BACKEND_URL}/api/category/list`);
    return await response.json();
  } catch (error) {
    console.error('Failed to fetch category data:', error);
    return { code: 500, data: null };
  }
}

function renderPage(lang, pageName, data = {}, query = {}) {
  let template = templates[lang] && templates[lang][pageName];
  
  if (!template || template.trim() === '') {
    template = templates[DEFAULT_LANG] && templates[DEFAULT_LANG][pageName];
    if (!template || template.trim() === '') {
      template =
        templates[DEFAULT_LANG].index ||
        '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>{{META_TITLE}}</title></head><body><h1>Template not found</h1></body></html>';
    }
  }
  
  template = template.replace(/\{\{LANG\}\}/g, lang);
  
  if (data.products) {
    let productRows = normalizeProductList(data.products);
    if (pageName === 'index') {
      productRows = productRows.slice(0, HOME_PRODUCTS_LIMIT);
    }
    const productHtml = productRows.map(product => {
      const price = product.price && product.price > 0 ? `<p class="product-card-price">¥${product.price}</p>` : '';
      const imgSrc = productCardImageSrc(product) || EMPTY_IMG;
      return `
        <div class="col-md-6 col-lg-4 product-item">
          <div class="product-card">
            <a href="/${lang}/products/detail?id=${product.id}" class="product-link product-card-img-wrapper">
              <img src="${imgSrc}" alt="${product.name}">
            </a>
            <div class="product-card-body">
              <a href="/${lang}/products/detail?id=${product.id}" class="product-link product-card-title">${product.name}</a>
              <span class="product-card-category">${product.categoryName || 'Uncategorized'}</span>
              <p class="product-card-description">${product.description || ''}</p>
              ${price}
            </div>
          </div>
        </div>
      `;
    }).join('');
    template = template.replace(/\{\{PRODUCT_GRID\}\}/g, productHtml);
  }
  
  if (data.categories) {
    // 修复：renderPage 内不能使用 req；从路由传入的 query 读取 categoryId
    const rawCat = query.categoryId;
    let currentCategoryId = null;
    if (rawCat !== undefined && rawCat !== '' && rawCat != null) {
      const n = parseInt(String(rawCat), 10);
      if (!isNaN(n)) currentCategoryId = n;
    }
    const allLabel = lang === 'cn' ? '全部产品' : 'All Products';
    
    const categoryHtml =
      `<a href="/${lang}/products" class="category-btn ${currentCategoryId === null ? 'active' : ''}">${allLabel}</a>` +
      data.categories.map(cat =>
        `<a href="/${lang}/products?categoryId=${cat.id}" class="category-btn ${currentCategoryId === parseInt(String(cat.id), 10) ? 'active' : ''}">${lang === 'cn' ? cat.name : cat.nameEn || cat.name}</a>`
      ).join('');
    template = template.replace(/\{\{CATEGORY_LIST\}\}/g, categoryHtml);
  }
  
  if (data.product) {
    const product = data.product;
    let tags = [];
    if (product.tags) {
      try {
        tags = JSON.parse(product.tags);
      } catch {
        tags = product.tags.split(',').map(t => t.trim()).filter(t => t);
      }
    }
    const tagHtml = tags.map(tag => `<span class="product-tag">${tag}</span>`).join('');
    
    template = template.replace(/\{\{PRODUCT_NAME\}\}/g, product.name || '');
    template = template.replace(/\{\{PRODUCT_PRICE\}\}/g, product.price && product.price > 0 ? `¥${product.price}` : '');
    template = template.replace(/\{\{PRODUCT_DESCRIPTION\}\}/g, product.description || '');
    template = template.replace(/\{\{PRODUCT_TAGS\}\}/g, tagHtml);
    template = template.replace(/\{\{PRODUCT_DETAIL\}\}/g, (product.detail || '').replace(/\n/g, '<br>'));
    // 修复：语言切换链接占位符
    template = template.replace(/\{\{PRODUCT_ID\}\}/g, String(product.id != null ? product.id : ''));
    
    const imageUrls = collectProductGalleryUrls(product);
    
    // 缩略图：站点相对路径，与 admin 一致
    const imagesHtml = imageUrls.map((url, idx) =>
      `<div class="gallery-thumb ${idx === 0 ? 'active' : ''}" data-src="${url}" onclick="onThumbClick(${idx}); return false;"><img src="${url}" data-src="${url}" alt="Image ${idx + 1}"></div>`
    ).join('');
    template = template.replace(/\{\{GALLERY_THUMBS\}\}/g, imagesHtml);
    template = template.replace(/\{\{MAIN_IMAGE\}\}/g, imageUrls[0] || EMPTY_IMG);
  }
  
  if (data.relatedProducts) {
    const relatedRows = normalizeProductList(data.relatedProducts).slice(0, RELATED_PRODUCTS_LIMIT);
    const relatedHtml = relatedRows.map(product => {
      const imgSrc = productCardImageSrc(product) || EMPTY_IMG;
      return `
        <div class="col-md-4 col-sm-6 col-xs-6 mb-3">
          <div class="related-product-card">
            <a href="/${lang}/products/detail?id=${product.id}" class="related-link related-product-card-img-wrapper">
              <img src="${imgSrc}" alt="${product.name}">
            </a>
            <div class="card-body">
              <a href="/${lang}/products/detail?id=${product.id}" class="related-link card-title">${product.name || ''}</a>
            </div>
          </div>
        </div>
      `;
    }).join('');
    template = template.replace(/\{\{RELATED_PRODUCTS\}\}/g, relatedHtml);
  }

  template = applySeoMeta(template, lang, pageName, query, data);
  return template;
}

// Cache header middleware for SSR pages
function setCacheHeaders(res, pageName) {
  if (pageName === 'index') res.setHeader('Cache-Control', 'public, max-age=300');
  else if (pageName === 'products') res.setHeader('Cache-Control', 'public, max-age=1800');
  else if (pageName === 'product-detail') res.setHeader('Cache-Control', 'public, max-age=3600');
}

// X-Robots-Tag for non-indexable pages
app.use((req, res, next) => {
  if (req.path === '/login.html' || req.path.startsWith('/admin')) {
    res.setHeader('X-Robots-Tag', 'noindex, nofollow');
  }
  next();
});

// 处理根目录index.html的访问，重定向到SSR路由
app.get('/index.html', (req, res) => {
  res.redirect('/');
});

app.get('/en', async (req, res) => {
  setCacheHeaders(res, 'index');
  const data = {};
  const [productsRes, categoriesRes] = await Promise.all([
    fetchProductData(),
    fetchCategoryData()
  ]);
  if (productsRes.code === 200) data.products = normalizeProductList(productsRes.data);
  if (categoriesRes.code === 200) data.categories = categoriesRes.data;
  
  const categoryId = req.query.categoryId;
  if (categoryId && data.products.length) {
    data.products = data.products.filter(p => p.categoryId == categoryId);
  }
  
  const html = renderPage('en', 'index', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/cn', async (req, res) => {
  setCacheHeaders(res, 'index');
  const data = {};
  const [productsRes, categoriesRes] = await Promise.all([
    fetchProductData(),
    fetchCategoryData()
  ]);
  if (productsRes.code === 200) data.products = normalizeProductList(productsRes.data);
  if (categoriesRes.code === 200) data.categories = categoriesRes.data;
  
  const categoryId = req.query.categoryId;
  if (categoryId && data.products.length) {
    data.products = data.products.filter(p => p.categoryId == categoryId);
  }
  
  const html = renderPage('cn', 'index', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/en/products', async (req, res) => {
  setCacheHeaders(res, 'products');
  const data = {};
  const [productsRes, categoriesRes] = await Promise.all([
    fetchProductData(),
    fetchCategoryData()
  ]);
  if (productsRes.code === 200) data.products = normalizeProductList(productsRes.data);
  if (categoriesRes.code === 200) data.categories = categoriesRes.data;

  const categoryId = req.query.categoryId;
  if (categoryId && data.products.length) {
    data.products = data.products.filter(p => p.categoryId == categoryId);
  }

  const html = renderPage('en', 'products', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/cn/products', async (req, res) => {
  setCacheHeaders(res, 'products');
  const data = {};
  const [productsRes, categoriesRes] = await Promise.all([
    fetchProductData(),
    fetchCategoryData()
  ]);
  if (productsRes.code === 200) data.products = normalizeProductList(productsRes.data);
  if (categoriesRes.code === 200) data.categories = categoriesRes.data;

  const categoryId = req.query.categoryId;
  if (categoryId && data.products.length) {
    data.products = data.products.filter(p => p.categoryId == categoryId);
  }

  const html = renderPage('cn', 'products', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/en/products/detail', async (req, res) => {
  setCacheHeaders(res, 'product-detail');
  const productId = req.query.id || 1;
  const data = {};

  const [productRes, relatedRes] = await Promise.all([
    fetchProductData(productId),
    fetchProductData()
  ]);
  if (productRes.code === 200) data.product = productRes.data;
  if (relatedRes.code === 200) {
    data.relatedProducts = normalizeProductList(relatedRes.data).filter(p => p.id != productId);
  }
  
  const html = renderPage('en', 'product-detail', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/cn/products/detail', async (req, res) => {
  setCacheHeaders(res, 'product-detail');
  const productId = req.query.id || 1;
  const data = {};

  const [productRes, relatedRes] = await Promise.all([
    fetchProductData(productId),
    fetchProductData()
  ]);
  if (productRes.code === 200) data.product = productRes.data;
  if (relatedRes.code === 200) {
    data.relatedProducts = normalizeProductList(relatedRes.data).filter(p => p.id != productId);
  }
  
  const html = renderPage('cn', 'product-detail', data, req.query);
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

app.get('/', async (req, res) => {
  res.redirect('/en');
});

/**
 * 生成标准的 Sitemap.xml
 * 动态拉取后端数据，实时生成，确保搜索引擎获取最新内容
 */
app.get('/sitemap.xml', async (req, res) => {
  try {
    const [productsRes, categoriesRes] = await Promise.all([
      fetchProductData(),
      fetchCategoryData()
    ]);

    const products = normalizeProductList(productsRes.code === 200 ? productsRes.data : []);
    const categories = normalizeCategoryList(categoriesRes.code === 200 ? categoriesRes.data : []);

    const urls = [];
    const now = new Date().toISOString();

    // 首页（中英文）
    urls.push({
      loc: `${SITEMAP_ORIGIN}/en`,
      lastmod: now,
      changefreq: 'daily',
      priority: '1.0'
    });
    urls.push({
      loc: `${SITEMAP_ORIGIN}/cn`,
      lastmod: now,
      changefreq: 'daily',
      priority: '1.0'
    });

    // 产品列表页（中英文）
    urls.push({
      loc: `${SITEMAP_ORIGIN}/en/products`,
      lastmod: now,
      changefreq: 'daily',
      priority: '0.9'
    });
    urls.push({
      loc: `${SITEMAP_ORIGIN}/cn/products`,
      lastmod: now,
      changefreq: 'daily',
      priority: '0.9'
    });

    // 分类页（中英文）
    categories.forEach(cat => {
      urls.push({
        loc: `${SITEMAP_ORIGIN}/en/products?categoryId=${cat.id}`,
        lastmod: now,
        changefreq: 'weekly',
        priority: '0.8'
      });
      urls.push({
        loc: `${SITEMAP_ORIGIN}/cn/products?categoryId=${cat.id}`,
        lastmod: now,
        changefreq: 'weekly',
        priority: '0.8'
      });
    });

    // 产品详情页（中英文）
    products.forEach(product => {
      if (product.id != null) {
        urls.push({
          loc: `${SITEMAP_ORIGIN}/en/products/detail?id=${product.id}`,
          lastmod: now,
          changefreq: 'weekly',
          priority: '0.85'
        });
        urls.push({
          loc: `${SITEMAP_ORIGIN}/cn/products/detail?id=${product.id}`,
          lastmod: now,
          changefreq: 'weekly',
          priority: '0.85'
        });
      }
    });

    // 生成XML
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map(url => `  <url>
    <loc>${escapeHtmlAttr(url.loc)}</loc>
    <lastmod>${url.lastmod}</lastmod>
    <changefreq>${url.changefreq}</changefreq>
    <priority>${url.priority}</priority>
  </url>`).join('\n')}
</urlset>`;

    res.setHeader('Content-Type', 'application/xml');
    res.setHeader('Cache-Control', 'public, max-age=3600'); // 缓存1小时
    res.send(xml);
  } catch (error) {
    console.error('Failed to generate sitemap:', error);
    res.status(500).send('Internal Server Error');
  }
});

/**
 * robots.txt
 */
app.get('/robots.txt', (req, res) => {
  const robots = `User-agent: *
Allow: /en/
Allow: /cn/
Allow: /sitemap.xml
Disallow: /admin.html
Disallow: /login.html
Disallow: /api/
Sitemap: ${SITEMAP_ORIGIN}/sitemap.xml
`;
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('Cache-Control', 'public, max-age=86400');
  res.send(robots);
});

/**
 * 生成 Google 站点验证文件
 */
app.get('/google*.html', (req, res) => {
  const filename = req.path.replace('/', '');
  res.send(`google-site-verification: ${filename}`);
});

// 404 handler (before static middleware to avoid false 200s)
app.use((req, res, next) => {
  if (req.path.startsWith('/api/') || req.path.startsWith('/uploads/') ||
      req.path.startsWith('/en') || req.path.startsWith('/cn') ||
      req.path === '/' || req.path === '/sitemap.xml' || req.path === '/robots.txt' ||
      req.path === '/favicon.ico' || req.path === '/logo.svg') {
    return next();
  }
  if (req.path.match(/\.(css|js|jpg|jpeg|png|gif|ico|svg|woff|woff2|ttf|eot|html)$/)) {
    return next();
  }
  res.status(404);
  const html = renderPage(DEFAULT_LANG, 'index', {}, {})
    .replace(/\{\{META_TITLE\}\}/g, '404 - Page Not Found - Musilab Audio')
    .replace(/\{\{META_DESCRIPTION\}\}/g, 'The page you are looking for was not found.');
  res.setHeader('Content-Type', 'text/html');
  res.send(html);
});

// 修复：静态资源放在 SSR 路由之后，避免 /en、/cn 被 en/index.html 抢先返回导致占位符未替换
app.use(express.static(__dirname));

app.listen(PORT, () => {
  console.log(`SSR Server running on http://localhost:${PORT}`);
  console.log(`Backend URL: ${BACKEND_URL}`);
  console.log(`Sitemap available at: ${SITEMAP_ORIGIN}/sitemap.xml`);
});