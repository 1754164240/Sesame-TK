(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    }
    root.SettingsSearchContract = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    const normalize = (value) => String(value || '').trim().toLocaleLowerCase();

    const containsQuery = (values, query) => values.some(
        value => normalize(value).includes(query)
    );

    const filterSettingsModels = (tabs, modelsMap, query) => {
        const normalizedQuery = normalize(query);
        const safeTabs = Array.isArray(tabs) ? tabs : [];
        const safeModelsMap = modelsMap && typeof modelsMap === 'object'
            ? modelsMap
            : {};

        return safeTabs.flatMap(tab => {
            const fields = Array.isArray(safeModelsMap[tab?.modelCode])
                ? safeModelsMap[tab.modelCode]
                : [];
            const modelMatches = normalizedQuery === '' || containsQuery(
                [tab?.modelName, tab?.modelCode],
                normalizedQuery
            );
            if (modelMatches) {
                return [{ tab, fields }];
            }

            const matchedFields = fields.filter(field => containsQuery(
                [field?.name, field?.code, field?.desc],
                normalizedQuery
            ));
            return matchedFields.length > 0
                ? [{ tab, fields: matchedFields }]
                : [];
        });
    };

    const visibleSettingsActiveKey = (filteredModels, activeKey) => {
        const safeModels = Array.isArray(filteredModels) ? filteredModels : [];
        return safeModels.some(item => item?.tab?.modelCode === activeKey)
            ? activeKey
            : undefined;
    };

    return {
        filterSettingsModels,
        visibleSettingsActiveKey
    };
}));
