const test = require('node:test');
const assert = require('node:assert/strict');
const {
    buildPreviewSelection,
    checkedFromChange
} = require('../../main/assets/web/js/friend-preview-contract.js');

test('复选框事件和直接布尔值统一解析为选中状态', () => {
    assert.equal(checkedFromChange({ target: { checked: true } }), true);
    assert.equal(checkedFromChange({ target: { checked: false } }), false);
    assert.equal(checkedFromChange(true), true);
    assert.equal(checkedFromChange(false), false);
    assert.equal(checkedFromChange({}), false);
});

test('显式范围保留选中好友和包含分组', () => {
    const selection = buildPreviewSelection({
        selectionScope: 'EXPLICIT',
        selectedUserIds: ['100', ' 200 ', '100'],
        includeGroupIds: ['g-main', 'g-main'],
        excludeGroupIds: ['g-disabled'],
        relationFilter: 'MUTUAL_ONLY'
    });

    assert.deepEqual(selection, {
        selectionScope: 'EXPLICIT',
        includeUserIds: ['100', '200'],
        includeGroupIds: ['g-main'],
        excludeUserIds: [],
        excludeGroupIds: ['g-disabled'],
        relationFilter: 'MUTUAL_ONLY',
        capabilityFilter: null
    });
});

test('动态范围应用排除关系和能力筛选且未知能力默认排除', () => {
    const selection = buildPreviewSelection({
        selectionScope: 'ALL_FRIENDS',
        selectedUserIds: ['100', '200'],
        excludeSelectedUsers: true,
        includeGroupIds: ['g-ignored'],
        excludeGroupIds: ['g-one-way'],
        relationFilter: 'ALL_KNOWN',
        capabilityKeys: ['forest', 'farm'],
        capabilityStates: ['OPEN']
    });

    assert.deepEqual(selection, {
        selectionScope: 'ALL_FRIENDS',
        includeUserIds: [],
        includeGroupIds: [],
        excludeUserIds: ['100', '200'],
        excludeGroupIds: ['g-one-way'],
        relationFilter: 'ALL_KNOWN',
        capabilityFilter: {
            moduleKeys: ['forest', 'farm'],
            requiredStates: ['OPEN'],
            includeUnknown: false
        }
    });
});

test('未知枚举和不安全能力键被收窄为默认值', () => {
    const selection = buildPreviewSelection({
        selectionScope: 'UNKNOWN_SCOPE',
        selectedUserIds: ['', '300'],
        relationFilter: 'UNKNOWN_RELATION',
        capabilityKeys: ['forest', '<script>'],
        capabilityStates: ['OPEN', 'INVALID'],
        includeUnknown: true
    });

    assert.deepEqual(selection, {
        selectionScope: 'EXPLICIT',
        includeUserIds: ['300'],
        includeGroupIds: [],
        excludeUserIds: [],
        excludeGroupIds: [],
        relationFilter: 'MUTUAL_ONLY',
        capabilityFilter: {
            moduleKeys: ['forest'],
            requiredStates: ['OPEN'],
            includeUnknown: true
        }
    });
});
