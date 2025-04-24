const getLocalStorage = () => {
    try {
        return JSON.parse(localStorage.getItem('db'));
    }
    catch (e) {
        return {};
    }
};

const groupBy = (arr, f) => {
    const res = {};
    for (const el of arr) {
        const k = f(el);
        res[k] = res[k] || [];
        res[k].push(el);
    }
    return res;
};

const minBy = (arr, f) => {
    let minEl = arr[0];
    let minVal = f(minEl);
    for (const el of arr) {
        const v = f(el);
        if (v < minVal) {
            minVal = v;
            minEl = el;
        }
    }
    return minEl;
};

const randEl = (arr) => {
    const ind = Math.floor(Math.random() * arr.length);
    return arr[ind];
};

function El(props) {
    return (
        <a
            onClick={props.onClick}
            style={{ display: 'flex', flexDirection: 'column' }} 
        >
            <h1 dangerouslySetInnerHTML={{ __html: props.el.label }} />
            <img src={props.el.image} />
        </a>

    );
}

function App() {
    const [rawDb, rawSetDB] = React.useState(getLocalStorage());
    const db = rawDb || {};
    const [active, setActive] = React.useState(null);
    const setDB = (db) => {
        localStorage.setItem('db', JSON.stringify(db));
        rawSetDB(db);
    };
    const handleFilePick = (e) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            const source = JSON.parse(e.target.result);
            const sources = { ...(db.sources || {}), [source.name]: source };
            setDB({ ...db, sources });
        };
        reader.readAsText(e.target.files[0]);
    };

    if (!active) {
        return (
            <div>
                <label htmlFor="select-active">Select Rankly</label>
                <select
                    onChange={(e) => setActive(e.target.value)}
                    id="select-active"
                >
                    <option key="" value="">--Please choose an option--</option>
                    {Object.keys(db.sources || {}).map((name) => (
                        <option key={name} value={name}>{name}</option>
                    ))}
                </select>
            </div>
        );
    }

    const rankly = db.sources[active];
    const mus = rankly.mus || [];
    const elements = rankly.elements.map((el, id) => ({ 
        ...el, id, mus: new Set([]) 
    }));
    for (const mu of mus) {
        elements[mu.w].mus.add(mu.l);
        elements[mu.l].mus.add(mu.w);
    }
    const byMuCount = groupBy(elements, (el) => el.mus.size);
    const minMuCountEl = minBy(elements, (el) => el.mus.size);
    const minMuCount = minMuCountEl.mus.size;
    const numElements = elements.length;

    let muCount = minMuCount;
    let mu1Choices = [];
    while (muCount < numElements - 1 && mu1Choices.length < numElements / 2) {
        mu1Choices = [
            ...mu1Choices,
            ...byMuCount[muCount].map((el) => el.id)
        ];
        muCount++;
    }

    const mu1 = randEl(mu1Choices);

    muCount = minMuCount;
    let mu2Choices = [];
    while (muCount < numElements - 1 && mu2Choices.length < numElements / 2) {
        mu2Choices = [
            ...mu2Choices,
            ...byMuCount[muCount].map((el) => el.id).filter((id) => id !== mu1)
        ];
        muCount++;
    }
    const mu2 = randEl(mu1Choices);

    const reportMu = (w, l) => {
        setDB({
            ...db,
            sources: {
                ...db.sources,
                [active]: {
                    ...rankly,
                    mus: [
                        ...(rankly.mus || []),
                        { w, l }
                    ],
                },
            },
        })
    };

    // <input onChange={handleFilePick} type="file" />
    return (
        <div style={{ display: 'flex', flexDirection: 'column' }} >
            <div style={{ display: 'flex', flexDirection: 'row' }} >
                <El el={elements[mu1]} onClick={() => reportMu(mu1, mu2)} />
                <El el={elements[mu2]} onClick={() => reportMu(mu2, mu1)} />
            </div>
            {mus.length} / {(numElements * (numElements - 1)) / 2} ({numElements})
            <button className="button" onClick={() => setDB({ ...db })} >new mu</button>
        </div>
    );
}

function main() {
    const container = document.getElementById('app');
    const root = ReactDOM.createRoot(container);
    root.render(<App />);
}

(() => {
    main();
})();
