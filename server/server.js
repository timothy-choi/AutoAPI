const express = require('express.js');
const dotenv = require('dotenv');
const session = require('express-session');
const app = express();

dotenv.config();

app.use(express.json());
app.use(express.urlencoded({ extended: false }));

app.use(session({
    secret: process.env.SESSION_SECRET,
    resave: false,
    saveUninitalized: true,
    cookie: {secure: false }
}));


const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {});