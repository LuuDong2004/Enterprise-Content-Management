window.initLoadingIndicator = function(loadingText) {
    const loadingIndicator = document.querySelector('.v-loading-indicator');
    const sectionLoadingContent = document.createElement('section');
    sectionLoadingContent.className='v-loading-content';

    const spanLoading = document.createElement('span');
    spanLoading.className = "loader";

    const divTextLoading = document.createElement('div');
    divTextLoading.className='div-text-loading';
    const textLoading = document.createElement('span');
    textLoading.className = "v-text-loading";
    textLoading.textContent= loadingText || 'Vui lòng chờ trong giây lát!';
    divTextLoading.appendChild(textLoading);

    sectionLoadingContent.appendChild(spanLoading);
    sectionLoadingContent.appendChild(divTextLoading);
    loadingIndicator.appendChild(sectionLoadingContent);
}
