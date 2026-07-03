from app.measurement_conversion import convert_measurement_text


def test_metric_to_us_conversion() -> None:
    converted = convert_measurement_text("100 grams tomato", "us")

    assert converted.text == "3.5 ounces tomato"
    assert converted.original_text == "100 grams tomato"


def test_us_to_metric_conversion() -> None:
    converted = convert_measurement_text("2 pounds minced beef", "metric")

    assert converted.text == "0.9 kilograms minced beef"


def test_temperature_conversion() -> None:
    converted = convert_measurement_text("180 Celsius oven", "us")

    assert converted.text == "356 fahrenheit oven"


def test_unsupported_unit_is_unchanged() -> None:
    converted = convert_measurement_text("1 medium onion", "metric")

    assert converted.text == "1 medium onion"
    assert converted.converted_unit is None


def test_vague_quantity_is_unchanged() -> None:
    converted = convert_measurement_text("a pinch salt", "us")

    assert converted.text == "a pinch salt"


def test_missing_quantity_is_unchanged() -> None:
    converted = convert_measurement_text("rice", "us")

    assert converted.text == "rice"


def test_round_trip_conversion_where_practical() -> None:
    us_converted = convert_measurement_text("1000 millilitres water", "us")
    metric_converted = convert_measurement_text(us_converted.text, "metric")

    assert us_converted.text == "4.2 cups water"
    assert metric_converted.text == "1008 millilitres water"
