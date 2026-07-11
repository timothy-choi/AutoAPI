const express = require('express');
const router = express.Router();
const SearchController = require('./SearchController');

router.post('/:index', SearchController.IndexDocument);

router.get('/:index', SearchController.Search);

router.put('/:index/:id', SearchController.UpdateDocument);

router.delete('/:index/:id', SearchController.DeleteDocument);

module.exports = router;