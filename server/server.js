const express = require('express.js');
const dotenv = require('dotenv');
const session = require('express-session');
const sequelize = require('./config/postgres');
const app = express();
const userAuthRoutes = require('./UserAuth/UserAuthRouter');

dotenv.config();
require('./config/mongodb');

app.use(express.json());
app.use(express.urlencoded({ extended: false }));

app.use(session({
    secret: process.env.SESSION_SECRET,
    resave: false,
    saveUninitalized: true,
    cookie: {
        secure: true,
        maxAge: 24 * 60 * 60 * 1000
    }
}));

app.use('/userAuth', userAuthRoutes);


sequelize.sync()
    .then(() => {
        const PORT = process.env.PORT || 3000;
        app.listen(PORT, () => {});
    }).catch((err) => {});