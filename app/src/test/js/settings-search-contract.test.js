const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
    filterSettingsModels
} = require('../../main/assets/web/js/settings-search-contract.js');

const tabs = [
    { modelCode: 'AntOcean', modelName: '神奇海洋' },
    { modelCode: 'AntForest', modelName: '蚂蚁森林' }
];

const modelsMap = {
    AntOcean: [
        { code: 'dailyOceanTask', name: '海洋日常任务', desc: '完成海洋浏览任务' },
        { code: 'aiFish', name: 'AI摸鱼', desc: '自动找回被抓的鱼并摸鱼' }
    ],
    AntForest: [
        { code: 'collectEnergy', name: '收取能量', desc: '收取好友能量' }
    ]
};

test('字段名称支持忽略大小写和首尾空白搜索', () => {
    assert.deepEqual(
        filterSettingsModels(tabs, modelsMap, ' ai摸鱼 '),
        [{ tab: tabs[0], fields: [modelsMap.AntOcean[1]] }]
    );
});

test('模块名称或代码命中时返回模块全部字段', () => {
    assert.deepEqual(
        filterSettingsModels(tabs, modelsMap, '神奇海洋'),
        [{ tab: tabs[0], fields: modelsMap.AntOcean }]
    );
    assert.deepEqual(
        filterSettingsModels(tabs, modelsMap, 'ANTOCEAN'),
        [{ tab: tabs[0], fields: modelsMap.AntOcean }]
    );
});

test('字段代码和描述均可搜索', () => {
    assert.deepEqual(
        filterSettingsModels(tabs, modelsMap, 'collectenergy'),
        [{ tab: tabs[1], fields: [modelsMap.AntForest[0]] }]
    );
    assert.deepEqual(
        filterSettingsModels(tabs, modelsMap, '找回被抓'),
        [{ tab: tabs[0], fields: [modelsMap.AntOcean[1]] }]
    );
});

test('空查询恢复全部模块和字段且不修改输入', () => {
    const tabsBefore = JSON.stringify(tabs);
    const modelsBefore = JSON.stringify(modelsMap);

    assert.deepEqual(filterSettingsModels(tabs, modelsMap, '   '), [
        { tab: tabs[0], fields: modelsMap.AntOcean },
        { tab: tabs[1], fields: modelsMap.AntForest }
    ]);
    assert.equal(JSON.stringify(tabs), tabsBefore);
    assert.equal(JSON.stringify(modelsMap), modelsBefore);
});

test('不存在的关键词返回空结果', () => {
    assert.deepEqual(filterSettingsModels(tabs, modelsMap, '不存在'), []);
});

test('第二版设置页接入全局搜索合同和空结果提示', () => {
    const html = fs.readFileSync(
        path.resolve(__dirname, '../../main/assets/web/semi_index.html'),
        'utf8'
    );

    assert.match(html, /settings-search-contract\.js/);
    assert.match(html, /filterSettingsModels\(/);
    assert.match(html, /placeholder="搜索模块或设置项"/);
    assert.match(html, /未找到匹配设置/);
});
