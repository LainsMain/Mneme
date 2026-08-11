# Mneme documentation diary

This directory contains the fictional diary used in the repository screenshots.
It exists only to make the product documentation consistent and reproducible;
none of these files are bundled into the Android application or uploaded to a
Mneme backup server.

## Sample photos

<table>
  <tr>
    <td><img src="photos/sunset-picnic.jpg" alt="A sunset picnic"></td>
    <td><img src="photos/twilight-sky.jpg" alt="A peach-colored twilight sky"></td>
    <td><img src="photos/rainy-evening.jpg" alt="A rainy evening street"></td>
    <td><img src="photos/forest-trail.jpg" alt="A wet forest trail"></td>
  </tr>
  <tr>
    <td><img src="photos/market-flowers.jpg" alt="Flowers and peaches at a market"></td>
    <td><img src="photos/sunday-breakfast.jpg" alt="A quiet breakfast table"></td>
    <td><img src="photos/canal-bicycle.jpg" alt="A bicycle beside a canal"></td>
    <td><img src="photos/rainy-cafe.jpg" alt="A book and coffee beside a rainy window"></td>
  </tr>
</table>

The eight photographs were generated specifically for Mneme's documentation
with OpenAI's image generation tool. They depict invented moments, contain no
real user information, and were prompted to resemble imperfect everyday phone
photos rather than commercial stock photography.

`seed.sql` contains the matching fictional August 2026 diary: daily prose,
formatting marks, photo metadata, locations, and a monthly recap. It is a
developer-only screenshot fixture and is never executed by a production build.

## Updating the screenshots

Screenshots in `../screenshots/` are captures of the real Android application
running this fixture on an emulator. If a screen changes, update the relevant
capture along with the README so the repository never advertises a mock-up that
the app does not actually provide.
