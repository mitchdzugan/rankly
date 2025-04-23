const getLocalStorage = () => {
    try {
        return JSON.parse(localStorage.getItem('db'));
    }
    catch (e) {
        return {}
    }
};

function App() {
    const [rawDb, rawSetDB] = React.useState(getLocalStorage());
    const db = rawDb || {};
    const [active, setActive] = React.useState(null);
    const setDB = (db) => {
        localStorage.setItem('db', JSON.stringify('db'));
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

    console.log({ db, active });
    return (
        <div>
            <h1>Hello World!</h1>
            <input onChange={handleFilePick} type="file" />
        </div>
    );
}

function main() {
    const container = document.getElementById('app');
    const root = ReactDOM.createRoot(container);
    root.render(<App />)
}

(() => {
    main();
})()