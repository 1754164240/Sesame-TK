(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    }
    root.FriendPreviewContract = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    const validRelations = new Set([
        'MUTUAL_ONLY',
        'ALL_KNOWN',
        'INCLUDE_SELF'
    ]);
    const validCapabilityStates = new Set([
        'OPEN',
        'NOT_OPEN',
        'UNAVAILABLE'
    ]);
    const capabilityKeyPattern = /^[A-Za-z0-9_.-]{1,48}$/;

    const normalizeList = (values) => Array.from(new Set(
        (Array.isArray(values) ? values : [])
            .map(value => String(value || '').trim())
            .filter(Boolean)
    ));

    const checkedFromChange = (value) => {
        if (typeof value === 'boolean') {
            return value;
        }
        return value?.target?.checked === true;
    };

    const buildPreviewSelection = (options = {}) => {
        const selectionScope = options.selectionScope === 'ALL_FRIENDS'
            ? 'ALL_FRIENDS'
            : 'EXPLICIT';
        const selectedUserIds = normalizeList(options.selectedUserIds);
        const capabilityKeys = normalizeList(options.capabilityKeys)
            .filter(key => capabilityKeyPattern.test(key));
        let capabilityStates = normalizeList(options.capabilityStates)
            .filter(state => validCapabilityStates.has(state));
        if (capabilityKeys.length > 0 && capabilityStates.length === 0) {
            capabilityStates = ['OPEN'];
        }

        return {
            selectionScope,
            includeUserIds: selectionScope === 'EXPLICIT'
                ? selectedUserIds
                : [],
            includeGroupIds: selectionScope === 'EXPLICIT'
                ? normalizeList(options.includeGroupIds)
                : [],
            excludeUserIds: selectionScope === 'ALL_FRIENDS' &&
                options.excludeSelectedUsers === true
                ? selectedUserIds
                : normalizeList(options.excludeUserIds),
            excludeGroupIds: normalizeList(options.excludeGroupIds),
            relationFilter: validRelations.has(options.relationFilter)
                ? options.relationFilter
                : 'MUTUAL_ONLY',
            capabilityFilter: capabilityKeys.length === 0
                ? null
                : {
                    moduleKeys: capabilityKeys,
                    requiredStates: capabilityStates,
                    includeUnknown: options.includeUnknown === true
                }
        };
    };

    return {
        buildPreviewSelection,
        checkedFromChange
    };
}));
