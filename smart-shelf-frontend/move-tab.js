const fs = require('fs');
const lines = fs.readFileSync('index.html', 'utf8').split('\n');

let start = -1;
let end = -1;
let mainEnd = -1;

for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('id="customer-store-tab"')) {
        start = i - 1; // get the comment too
    }
    if (start !== -1 && lines[i].includes('<!-- TAB 2: PERMANENT CATALOG')) {
        end = i - 1;
        break;
    }
}

const cutLines = lines.splice(start, end - start);

for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('</main>')) {
        mainEnd = i;
        break;
    }
}

lines.splice(mainEnd + 1, 0, ...cutLines);
fs.writeFileSync('index.html', lines.join('\n'));
console.log('Done!');
