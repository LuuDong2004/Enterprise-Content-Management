import '@vaadin/icon/vaadin-iconset.js';

const template = document.createElement('template');

template.innerHTML = `<vaadin-iconset name="tools-icons" size="16">
  <svg><defs>
    <g id="tools-icons:breadcrumb-home"><?xml version="1.0" encoding="utf-8"?>
        <svg transform="translate(8px, 6px)" width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M4.33333 10.3338H9.66667M6.34513 0.843154L1.82359 4.3599C1.52135 4.59498 1.37022 4.71252 1.26135 4.85973C1.16491 4.99012 1.09307 5.13701 1.04935 5.29319C1 5.4695 1 5.66095 1 6.04386V10.8671C1 11.6139 1 11.9872 1.14532 12.2725C1.27316 12.5233 1.47713 12.7273 1.72801 12.8552C2.01323 13.0005 2.3866 13.0005 3.13333 13.0005H10.8667C11.6134 13.0005 11.9868 13.0005 12.272 12.8552C12.5229 12.7273 12.7268 12.5233 12.8547 12.2725C13 11.9872 13 11.6139 13 10.8671V6.04386C13 5.66095 13 5.4695 12.9506 5.29319C12.9069 5.13701 12.8351 4.99012 12.7386 4.85973C12.6298 4.71252 12.4787 4.59499 12.1764 4.35991L7.65487 0.843154C7.42065 0.660986 7.30354 0.569901 7.17423 0.534889C7.06013 0.503995 6.93987 0.503995 6.82577 0.534889C6.69646 0.569901 6.57935 0.660986 6.34513 0.843154Z" stroke="#505A5F" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    </g>
  </defs></svg>
</vaadin-iconset>`;

document.head.appendChild(template.content);
